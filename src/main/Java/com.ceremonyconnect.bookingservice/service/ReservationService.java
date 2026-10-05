package com.ceremonyconnect.bookingservice.service;

import com.ceremonyconnect.bookingservice.dto.ReservationRequest;
import com.ceremonyconnect.bookingservice.exception.BadRequestException;
import com.ceremonyconnect.bookingservice.exception.ConflictException;
import com.ceremonyconnect.bookingservice.exception.ResourceNotFoundException;
import com.ceremonyconnect.bookingservice.model.Reservation;
import com.ceremonyconnect.bookingservice.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final HallRepository hallRepository;
    private final WeddingPackageRepository packageRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final AuditService auditService;

    private static final List<Reservation.Status> CONFLICT_EXCLUDED =
        List.of(Reservation.Status.REJECTED, Reservation.Status.CANCELLED);

    @Transactional
    public Reservation createReservation(ReservationRequest req, Long customerId) {

        // ── Load entities ──────────────────────────────────────────────
        Hall hall = hallRepository.findById(req.hallId())
            .orElseThrow(() -> new ResourceNotFoundException("Hall not found: " + req.hallId()));

        WeddingPackage pkg = packageRepository.findById(req.packageId())
            .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + req.packageId()));

        User customer = userRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        // ── Conflict Detection ─────────────────────────────────────────
        boolean conflict = reservationRepository
            .existsByHallIdAndReservationDateAndStatusNotIn(
                req.hallId(), req.reservationDate(), CONFLICT_EXCLUDED);
        if (conflict) {
            throw new ConflictException(
                "Hall '" + hall.getName() + "' is already booked on " + req.reservationDate());
        }

        // ── Cost Calculation ───────────────────────────────────────────
        BigDecimal total = pkg.getBasePrice().add(hall.getPricePerDay());

        if (req.cateringRequested() && pkg.getCateringPricePerHead() != null) {
            total = total.add(
                pkg.getCateringPricePerHead().multiply(BigDecimal.valueOf(req.guestCount())));
        }
        if (req.decorationRequested() && pkg.getDecorationPrice() != null) {
            total = total.add(pkg.getDecorationPrice());
        }

        // ── Build & Save (Status is PENDING) ───────────────────────────
        Reservation reservation = new Reservation();
        reservation.setCustomer(customer);
        reservation.setHall(hall);
        reservation.setWeddingPackage(pkg);
        reservation.setReservationDate(req.reservationDate());
        reservation.setGuestCount(req.guestCount());
        reservation.setCateringRequested(req.cateringRequested());
        reservation.setDecorationRequested(req.decorationRequested());
        reservation.setTotalCost(total);
        reservation.setStatus(Reservation.Status.PENDING);

        Reservation saved = reservationRepository.save(reservation);

        auditService.log("RESERVATION_CREATED",
            "Reservation #" + saved.getId() + " for hall " + hall.getName() + " on " + req.reservationDate(),
            customer.getEmail(), "Reservation", saved.getId());

        return saved;
    }

    public List<Reservation> getByCustomer(Long customerId) {
        return reservationRepository.findByCustomerId(customerId);
    }

    public List<Reservation> getByStatus(String status) {
        return reservationRepository.findByStatus(Reservation.Status.valueOf(status.toUpperCase()));
    }

    public List<Reservation> getAll() {
        return reservationRepository.findAll();
    }

    public Reservation getById(Long id) {
        return reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
    }

    @Transactional
    public Reservation updateStatus(Long id, String newStatus, String remarks,
                                    String newDate, String staffEmail) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));

        Reservation.Status status;
        try {
            status = Reservation.Status.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + newStatus);
        }

        // Rule 3: If Admin reschedules an APPROVED booking, status reverts to PENDING until explicitly re-approved.
        if (status == Reservation.Status.RESCHEDULED) {
            if (newDate == null || newDate.isBlank()) {
                throw new BadRequestException("newDate is required when rescheduling");
            }
            LocalDate rescheduleDate = LocalDate.parse(newDate);
            if (rescheduleDate.isBefore(LocalDate.now())) {
                throw new BadRequestException("Rescheduled date cannot be in the past");
            }
            // Check conflict for the new date
            boolean conflict = reservationRepository
                .existsByHallIdAndReservationDateAndStatusNotIn(
                    reservation.getHall().getId(), rescheduleDate, CONFLICT_EXCLUDED);
            if (conflict) {
                throw new ConflictException("Hall is already booked on " + rescheduleDate);
            }
            reservation.setReservationDate(rescheduleDate);
            // Revert status to PENDING until explicitly re-approved by staff
            status = Reservation.Status.PENDING;
            remarks = (remarks != null && !remarks.isBlank() ? remarks + " " : "") +
                      "(Rescheduled to " + rescheduleDate + ", reverted to PENDING for re-approval)";
        }

        // Rule 5 & Rule 4 (Part 3): If Staff cancels a booking with verified payments, flag for refund
        if (status == Reservation.Status.CANCELLED) {
            triggerRefundsForCancelledReservation(reservation, remarks, staffEmail);
        }

        reservation.setStatus(status);
        reservation.setStaffRemarks(remarks);
        Reservation updated = reservationRepository.save(reservation);

        auditService.log("STATUS_UPDATED",
            "Reservation #" + id + " changed to " + status + ". Remarks: " + remarks,
            staffEmail, "Reservation", id);

        return updated;
    }

    /**
     * Rule 4: Customer Cancellation
     * Customers can ONLY cancel a booking if status is PENDING.
     * If status is APPROVED, returns error: "This booking is approved. Please contact Reservation Staff to cancel."
     */
    @Transactional
    public Reservation cancelByCustomer(Long id, Long customerId, String customerEmail) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));

        if (!reservation.getCustomer().getId().equals(customerId)) {
            throw new BadRequestException("You do not have permission to cancel this booking");
        }

        if (reservation.getStatus() == Reservation.Status.APPROVED) {
            throw new BadRequestException("This booking is approved. Please contact Reservation Staff to cancel.");
        }

        if (reservation.getStatus() != Reservation.Status.PENDING) {
            throw new BadRequestException("Only pending bookings can be cancelled by customers.");
        }

        reservation.setStatus(Reservation.Status.CANCELLED);
        reservation.setStaffRemarks("Cancelled by customer");
        Reservation updated = reservationRepository.save(reservation);

        auditService.log("RESERVATION_CANCELLED",
            "Reservation #" + id + " cancelled by customer " + customerEmail,
            customerEmail, "Reservation", id);

        return updated;
    }

    /**
     * Rule 5: Staff Cancellation
     * Reservation Staff can cancel a booking at any time, including APPROVED bookings.
     * If booking has VERIFIED payments, automatically flags them for refund.
     */
    @Transactional
    public Reservation cancelByStaff(Long id, String reason, String staffEmail) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));

        reservation.setStatus(Reservation.Status.CANCELLED);
        String rem = reason != null && !reason.isBlank() ? reason : "Cancelled by reservation staff";
        reservation.setStaffRemarks(rem);
        Reservation updated = reservationRepository.save(reservation);

        triggerRefundsForCancelledReservation(reservation, rem, staffEmail);

        auditService.log("RESERVATION_CANCELLED_BY_STAFF",
            "Reservation #" + id + " cancelled by staff " + staffEmail + ". Reason: " + rem,
            staffEmail, "Reservation", id);

        return updated;
    }

    /** Helper: Automatically flags verified payments for refund when staff cancels */
    private void triggerRefundsForCancelledReservation(Reservation reservation, String reason, String performedBy) {
        List<Payment> payments = paymentRepository.findByReservationIdOrderBySubmittedAtDesc(reservation.getId());
        for (Payment payment : payments) {
            if (payment.getStatus() == Payment.Status.VERIFIED) {
                Refund refund = new Refund();
                refund.setRefundRef("CC-REF-2026-" + String.format("%03d", (int)(Math.random() * 900 + 100)));
                refund.setPayment(payment);
                refund.setReservation(reservation);
                refund.setCustomer(reservation.getCustomer());
                refund.setAmount(payment.getAmount());
                refund.setStatus(Refund.Status.IN_PROCESSING);
                refund.setReason(reason != null && !reason.isBlank() ? reason : "Booking cancelled by reservation staff");
                Refund savedRefund = refundRepository.save(refund);

                auditService.log("REFUND_TRIGGERED",
                    "Automated refund #" + savedRefund.getRefundRef() + " created for Payment " +
                    payment.getPaymentRef() + " (LKR " + payment.getAmount() + ")",
                    performedBy, "Refund", savedRefund.getId());
            }
        }
    }

    /** Update catering/decoration add-ons and recalculate cost */
    @Transactional
    public Reservation updateCustomization(Long id, boolean catering, boolean decoration,
                                           String customerEmail) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));

        WeddingPackage pkg = reservation.getWeddingPackage();
        BigDecimal total = pkg.getBasePrice().add(reservation.getHall().getPricePerDay());

        if (catering && pkg.getCateringPricePerHead() != null) {
            total = total.add(
                pkg.getCateringPricePerHead().multiply(BigDecimal.valueOf(reservation.getGuestCount())));
        }
        if (decoration && pkg.getDecorationPrice() != null) {
            total = total.add(pkg.getDecorationPrice());
        }

        reservation.setCateringRequested(catering);
        reservation.setDecorationRequested(decoration);
        reservation.setTotalCost(total);

        auditService.log("CUSTOMIZATION_UPDATED",
            "Catering=" + catering + ", Decoration=" + decoration + ", Total=" + total,
            customerEmail, "Reservation", id);

        return reservationRepository.save(reservation);
    }

    public List<LocalDate> getBookedDatesForHall(Long hallId) {
        return reservationRepository.findBookedDatesByHallId(hallId);
    }
}
