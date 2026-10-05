package com.ceremoneyconnect.bookingservice.service;

import com.ceremonyconnect.bookingservice.exception.BadRequestException;
import com.ceremonyconnect.bookingservice.exception.ConflictException;
import com.ceremonyconnect.bookingservice.exception.ResourceNotFoundException;
import com.ceremonyconnect.bookingservice.model.*;
import com.ceremonyconnect.bookingservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ReservationRepository reservationRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final PaymentSlipRepository paymentSlipRepository;
    private final AuditService auditService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Customer uploads payment slip (Advance or Full)
     * Enforces: Only APPROVED (or PAYMENT_UPLOADED) bookings can access the payment section.
     */
    @Transactional
    public Payment uploadPayment(Long reservationId, BigDecimal amount,
                                 Payment.PaymentType paymentType, String bankName,
                                 String transactionRef, MultipartFile file,
                                 String customerEmail) throws IOException {

        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        User customer = userRepository.findByEmail(customerEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerEmail));

        // Strict state validation: Only APPROVED bookings can make payments
        if (reservation.getStatus() == Reservation.Status.PENDING) {
            throw new BadRequestException("This booking is still pending approval. Only APPROVED bookings can proceed to payment.");
        }
        if (reservation.getStatus() == Reservation.Status.REJECTED || reservation.getStatus() == Reservation.Status.CANCELLED) {
            throw new BadRequestException("Cannot submit payment for a " + reservation.getStatus() + " booking.");
        }

        // Save file to disk
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
        String savedFileName = UUID.randomUUID() + "_" + (file != null ? file.getOriginalFilename() : "slip.jpg");
        Path targetPath = uploadPath.resolve(savedFileName);
        if (file != null && !file.isEmpty()) {
            Files.copy(file.getInputStream(), targetPath);
        }

        // Calculate amount if not specified
        BigDecimal payAmount = amount;
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (paymentType == Payment.PaymentType.ADVANCE) {
                // 30% advance standard
                payAmount = reservation.getTotalCost().multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
            } else {
                payAmount = reservation.getTotalCost();
            }
        }

        // Generate clean reference: CC-PAY-2026-XXX
        long count = paymentRepository.count() + 1;
        String payRef = String.format("CC-PAY-2026-%03d", count);

        Payment payment = new Payment();
        payment.setPaymentRef(payRef);
        payment.setReservation(reservation);
        payment.setCustomer(customer);
        payment.setAmount(payAmount);
        payment.setPaymentType(paymentType != null ? paymentType : Payment.PaymentType.ADVANCE);
        payment.setBankName(bankName != null && !bankName.isBlank() ? bankName : "Commercial Bank");
        payment.setTransactionReference(transactionRef != null && !transactionRef.isBlank() ? transactionRef : "REF-" + System.currentTimeMillis());
        payment.setFilePath(targetPath.toString());
        payment.setOriginalFileName(file != null ? file.getOriginalFilename() : "bank_slip.jpg");
        payment.setStatus(Payment.Status.PENDING);
        payment.setSubmittedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        // Also update reservation status to PAYMENT_UPLOADED if it was APPROVED
        if (reservation.getStatus() == Reservation.Status.APPROVED) {
            reservation.setStatus(Reservation.Status.PAYMENT_UPLOADED);
            reservationRepository.save(reservation);
        }

        auditService.log("PAYMENT_SUBMITTED",
            "Payment " + savedPayment.getPaymentRef() + " of LKR " + payAmount + " (" + paymentType + ") submitted for Booking #" + reservationId,
            customerEmail, "Payment", savedPayment.getId());

        return savedPayment;
    }

    /**
     * Calculate summary for the customer payment dashboard:
     * - Advance Required
     * - Total Paid (Sum of VERIFIED payments)
     * - Outstanding Balance (Total Booking Cost - Total Verified Payments)
     * - Active Booking Details
     * - Payment History
     */
    public Map<String, Object> getCustomerPaymentSummary(Long customerId) {
        List<Reservation> reservations = reservationRepository.findByCustomerId(customerId);
        List<Payment> payments = paymentRepository.findByCustomerIdOrderBySubmittedAtDesc(customerId);

        // Find current active reservation (APPROVED, PAYMENT_UPLOADED, CONFIRMED, or most recent PENDING)
        Reservation activeRes = reservations.stream()
            .filter(r -> r.getStatus() != Reservation.Status.CANCELLED && r.getStatus() != Reservation.Status.REJECTED)
            .findFirst()
            .orElse(reservations.isEmpty() ? null : reservations.get(0));

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal advanceRequired = BigDecimal.ZERO;
        BigDecimal outstandingBalance = BigDecimal.ZERO;
        Map<String, Object> bookingInfo = new HashMap<>();

        if (activeRes != null) {
            totalCost = activeRes.getTotalCost() != null ? activeRes.getTotalCost() : BigDecimal.ZERO;
            advanceRequired = totalCost.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);

            // Sum up verified payments for this reservation
            totalPaid = payments.stream()
                .filter(p -> p.getReservation().getId().equals(activeRes.getId()) && p.getStatus() == Payment.Status.VERIFIED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            outstandingBalance = totalCost.subtract(totalPaid);
            if (outstandingBalance.compareTo(BigDecimal.ZERO) < 0) {
                outstandingBalance = BigDecimal.ZERO;
            }

            bookingInfo.put("id", activeRes.getId());
            bookingInfo.put("bookingRef", String.format("CC-BK-2026-%03d", activeRes.getId()));
            bookingInfo.put("hallName", activeRes.getHall() != null ? activeRes.getHall().getName() : "Banquet Hall");
            bookingInfo.put("packageName", activeRes.getWeddingPackage() != null ? activeRes.getWeddingPackage().getName() : "Wedding Package");
            bookingInfo.put("eventDate", activeRes.getReservationDate());
            bookingInfo.put("guestCount", activeRes.getGuestCount());
            bookingInfo.put("totalCost", totalCost);
            bookingInfo.put("status", activeRes.getStatus());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("activeBooking", bookingInfo);
        result.put("advanceRequired", advanceRequired);
        result.put("totalPaid", totalPaid);
        result.put("outstandingBalance", outstandingBalance);
        result.put("totalBookingCost", totalCost);
        result.put("payments", payments);

        return result;
    }

    /** Finance Officer: Verification queue (all PENDING slips) */
    public List<Payment> getVerificationQueue() {
        return paymentRepository.findByStatusOrderBySubmittedAtDesc(Payment.Status.PENDING);
    }

    /**
     * Finance Officer verifies a payment slip
     */
    @Transactional
    public Payment verifyPayment(Long paymentId, String remarks, Long officerId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        User officer = userRepository.findById(officerId)
            .orElseThrow(() -> new ResourceNotFoundException("Officer not found: " + officerId));

        payment.setStatus(Payment.Status.VERIFIED);
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setVerifiedBy(officer);
        payment.setRejectionReason(remarks);
        Payment saved = paymentRepository.save(payment);

        Reservation res = payment.getReservation();

        // Calculate all verified payments for this reservation
        List<Payment> allResPayments = paymentRepository.findByReservationIdOrderBySubmittedAtDesc(res.getId());
        BigDecimal sumVerified = allResPayments.stream()
            .filter(p -> p.getStatus() == Payment.Status.VERIFIED)
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // If paid >= totalCost or full payment, confirm the booking
        if (sumVerified.compareTo(res.getTotalCost()) >= 0 || payment.getPaymentType() == Payment.PaymentType.FULL) {
            res.setStatus(Reservation.Status.CONFIRMED);
        } else {
            // Advance verified: booking is confirmed / locked in
            res.setStatus(Reservation.Status.CONFIRMED);
        }
        reservationRepository.save(res);

        // Generate / Update Invoice
        createOrUpdateInvoice(res, officer, sumVerified);

        auditService.log("PAYMENT_VERIFIED",
            "Payment " + saved.getPaymentRef() + " (LKR " + saved.getAmount() + ") verified by " + officer.getEmail(),
            officer.getEmail(), "Payment", saved.getId());

        return saved;
    }

    /**
     * Finance Officer rejects a payment slip with reason
     * Customer can then re-upload a new slip for the same booking.
     */
    @Transactional
    public Payment rejectPayment(Long paymentId, String reason, Long officerId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        User officer = userRepository.findById(officerId)
            .orElseThrow(() -> new ResourceNotFoundException("Officer not found: " + officerId));

        payment.setStatus(Payment.Status.REJECTED);
        payment.setRejectionReason(reason != null && !reason.isBlank() ? reason : "Payment slip was illegible or mismatched.");
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setVerifiedBy(officer);
        Payment saved = paymentRepository.save(payment);

        auditService.log("PAYMENT_REJECTED",
            "Payment " + saved.getPaymentRef() + " rejected by " + officer.getEmail() + ". Reason: " + payment.getRejectionReason(),
            officer.getEmail(), "Payment", saved.getId());

        return saved;
    }

    /** All financial transactions for Finance Officer */
    public List<Payment> getAllTransactions() {
        return paymentRepository.findAllByOrderBySubmittedAtDesc();
    }

    /** Get all refund records */
    public List<Refund> getRefunds() {
        return refundRepository.findAllByOrderByRequestedAtDesc();
    }

    /**
     * Finance Officer completes a refund
     */
    @Transactional
    public Refund completeRefund(Long refundId, Long officerId) {
        Refund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));

        User officer = userRepository.findById(officerId)
            .orElseThrow(() -> new ResourceNotFoundException("Officer not found: " + officerId));

        refund.setStatus(Refund.Status.REFUNDED);
        refund.setCompletedAt(LocalDateTime.now());
        refund.setProcessedBy(officer);
        Refund savedRefund = refundRepository.save(refund);

        // Also mark corresponding payment as REFUNDED
        if (refund.getPayment() != null) {
            Payment p = refund.getPayment();
            p.setStatus(Payment.Status.REFUNDED);
            paymentRepository.save(p);
        }

        auditService.log("REFUND_COMPLETED",
            "Refund " + savedRefund.getRefundRef() + " (LKR " + savedRefund.getAmount() + ") marked as REFUNDED by " + officer.getEmail(),
            officer.getEmail(), "Refund", savedRefund.getId());

        return savedRefund;
    }

    /** File path lookup for slip inspection */
    public File getPaymentSlipFile(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (payment.getFilePath() != null) {
            File f = new File(payment.getFilePath());
            if (f.exists()) return f;
        }
        return null;
    }

    private void createOrUpdateInvoice(Reservation reservation, User officer, BigDecimal paidAmount) {
        Invoice invoice = invoiceRepository.findByReservationId(reservation.getId()).orElse(null);
        if (invoice == null) {
            invoice = new Invoice();
            invoice.setInvoiceNumber("INV-2026-" + String.format("%03d", reservation.getId()));
            invoice.setReservation(reservation);
            invoice.setGeneratedBy(officer);
            BigDecimal subtotal = reservation.getTotalCost();
            BigDecimal tax = subtotal.multiply(new BigDecimal("0.10"));
            invoice.setSubtotal(subtotal);
            invoice.setTaxRate(new BigDecimal("0.10"));
            invoice.setTaxAmount(tax);
            invoice.setTotalAmount(subtotal.add(tax));
        }
        invoice.setPaymentStatus(Invoice.PaymentStatus.PAID);
        invoiceRepository.save(invoice);
    }

    // ── Backward compatibility for existing PaymentSlip endpoints ──
    public List<PaymentSlip> getPendingSlips() {
        return paymentSlipRepository.findByVerificationStatus(PaymentSlip.VerificationStatus.PENDING);
    }

    @Transactional
    public PaymentSlip uploadPaymentSlip(Long reservationId, MultipartFile file, String customerEmail) throws IOException {
        Payment p = uploadPayment(reservationId, null, Payment.PaymentType.ADVANCE, "Commercial Bank", "REF-" + System.currentTimeMillis(), file, customerEmail);
        PaymentSlip slip = new PaymentSlip();
        slip.setId(p.getId());
        slip.setReservation(p.getReservation());
        slip.setFilePath(p.getFilePath());
        slip.setOriginalFileName(p.getOriginalFileName());
        slip.setVerificationStatus(PaymentSlip.VerificationStatus.PENDING);
        return slip;
    }

    @Transactional
    public Invoice verifyAndGenerateInvoice(Long slipId, boolean approved, String remarks, Long officerId) {
        if (approved) {
            Payment p = verifyPayment(slipId, remarks, officerId);
            return invoiceRepository.findByReservationId(p.getReservation().getId()).orElse(null);
        } else {
            rejectPayment(slipId, remarks, officerId);
            return null;
        }
    }
}
