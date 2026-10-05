package com.ceremonyconnect.bookingservice.controller;

import com.ceremonyconnect.bookingservice.model.AuditLog;
import com.ceremonyconnect.bookingservice.model.User;
import com.ceremonyconnect.bookingservice.repository.AuditLogRepository;
import com.ceremonyconnect.bookingservice.repository.ReservationRepository;
import com.ceremonyconnect.bookingservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReservationRepository reservationRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserService userService;

    /**
     * GET /api/admin/report
     * Consolidated revenue + booking volume report
     */
    @GetMapping("/report")
    public Map<String, Object> getReport() {
        BigDecimal totalRevenue = reservationRepository.getTotalRevenue();
        List<Object[]> bookingCounts = reservationRepository.getBookingCountByStatus();

        Map<String, Long> countByStatus = new HashMap<>();
        for (Object[] row : bookingCounts) {
            countByStatus.put(row[0].toString(), (Long) row[1]);
        }

        return Map.of(
            "totalRevenue", totalRevenue,
            "bookingCountByStatus", countByStatus,
            "totalBookings", reservationRepository.count()
        );
    }

    /**
     * GET /api/admin/audit-logs
     * Last 100 audit log entries
     */
    @GetMapping("/audit-logs")
    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }

    /**
     * GET /api/admin/users
     * All users
     */
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    /**
     * PATCH /api/admin/users/{id}/password
     * Admin updates a user/staff password
     */
    @PatchMapping("/users/{id}/password")
    public ResponseEntity<?> updateUserPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
        }
        User updated = userService.updateUserPassword(id, newPassword, userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
            "message", "Password updated successfully for " + updated.getEmail(),
            "userId", updated.getId()
        ));
    }

    /**
     * PATCH /api/admin/users/{id}/status
     * Admin enables or disables a user/staff account
     */
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        boolean active = Boolean.parseBoolean(body.getOrDefault("active", true).toString());
        User updated = userService.updateUserStatus(id, active, userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
            "message", "Account status updated to " + (active ? "ACTIVE" : "DISABLED") + " for " + updated.getEmail(),
            "userId", updated.getId(),
            "active", updated.isActive()
        ));
    }
}
