package com.ceremonyconnect.bookingservice.dto;

import com.ceremonyconnect.bookingservice.dto.ReservationRequest;
import com.ceremonyconnect.bookingservice.model.Reservation;
import com.ceremonyconnect.bookingservice.repository.UserRepository;
import com.ceremonyconnect.bookingservice.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final UserRepository userRepository;

    /** POST /api/reservations — customer creates a booking */
    @PostMapping
    public ResponseEntity<Reservation> create(
            @Valid @RequestBody ReservationRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long customerId = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow().getId();
        Reservation saved = reservationService.createReservation(req, customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** GET /api/reservations/my — customer's own reservations */
    @GetMapping("/my")
    public List<Reservation> myReservations(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long customerId = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow().getId();
        return reservationService.getByCustomer(customerId);
    }

    /** GET /api/reservations/{id} — get specific reservation */
    @GetMapping("/{id}")
    public ResponseEntity<Reservation> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getById(id));
    }

    /** GET /api/reservations/booked-dates?hallId=1 */
    @GetMapping("/booked-dates")
    public List<LocalDate> bookedDates(@RequestParam Long hallId) {
        return reservationService.getBookedDatesForHall(hallId);
    }

    /** PATCH /api/reservations/{id}/customize */
    @PatchMapping("/{id}/customize")
    public ResponseEntity<Reservation> customize(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        boolean catering = Boolean.parseBoolean(body.getOrDefault("cateringRequested", false).toString());
        boolean decoration = Boolean.parseBoolean(body.getOrDefault("decorationRequested", false).toString());
        Reservation updated = reservationService.updateCustomization(
            id, catering, decoration, userDetails.getUsername());
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/reservations/{id}/cancel
     * Customer cancels booking (Strict: ONLY allowed if status is PENDING)
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Reservation> cancelByCustomer(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long customerId = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow().getId();
        Reservation cancelled = reservationService.cancelByCustomer(id, customerId, userDetails.getUsername());
        return ResponseEntity.ok(cancelled);
    }

    /**
     * POST /api/reservations/{id}/staff-cancel
     * Reservation Staff cancels booking (Allowed anytime; triggers refund if verified payments exist)
     */
    @PostMapping("/{id}/staff-cancel")
    public ResponseEntity<Reservation> cancelByStaff(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        String reason = (body != null) ? body.get("reason") : "Cancelled by staff";
        Reservation cancelled = reservationService.cancelByStaff(id, reason, userDetails.getUsername());
        return ResponseEntity.ok(cancelled);
    }

    /** GET /api/reservations and /api/reservations/all — all (admin/staff/coordinator) */
    @GetMapping({"", "/all"})
    public List<Reservation> getAll() {
        return reservationService.getAll();
    }
}
