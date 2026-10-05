package com.ceremonyconnect.bookingservice.controller;

import com.ceremonyconnect.bookingservice.dto.StaffReviewRequest;
import com.ceremonyconnect.bookingservice.model.Reservation;
import com.ceremonyconnect.bookingservice.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final ReservationService reservationService;

    /** GET /api/staff/reservations?status=PENDING */
    @GetMapping("/reservations")
    public List<Reservation> getByStatus(
            @RequestParam(defaultValue = "PENDING") String status) {
        return reservationService.getByStatus(status);
    }

    /** GET /api/staff/reservations/all */
    @GetMapping("/reservations/all")
    public List<Reservation> getAll() {
        return reservationService.getAll();
    }

    /**
     * PATCH /api/staff/reservations/{id}/review
     * Front Office Supervisor updates status to APPROVED | REJECTED | RESCHEDULED
     */
    @PatchMapping("/reservations/{id}/review")
    public ResponseEntity<Reservation> review(
            @PathVariable Long id,
            @Valid @RequestBody StaffReviewRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        Reservation updated = reservationService.updateStatus(
            id, req.status(), req.remarks(), req.newDate(), userDetails.getUsername());
        return ResponseEntity.ok(updated);
    }
}
