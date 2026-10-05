package controller;

import com.ceremonyconnect.bookingservice.exception.BadRequestException;
import com.ceremonyconnect.bookingservice.exception.ResourceNotFoundException;
import com.ceremonyconnect.bookingservice.model.User;
import com.ceremonyconnect.bookingservice.repository.UserRepository;
import com.ceremonyconnect.bookingservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final Pattern GMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+\\-]+@gmail\\.com$", Pattern.CASE_INSENSITIVE);

    /**
     * GET /api/users/profile
     * Authenticated customer/user fetches their own profile details
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));

        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "fullName", user.getFullName(),
            "email", user.getEmail(),
            "phone", user.getPhone() != null ? user.getPhone() : "",
            "role", user.getRole(),
            "active", user.isActive()
        ));
    }

    /**
     * PUT /api/users/profile
     * Authenticated customer updates their own profile details
     * Validates that customer email ends with @gmail.com
     * Hashes password using BCrypt if provided
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));

        String fullName = body.get("fullName");
        String phone = body.get("phone");
        String email = body.get("email");
        String newPassword = body.get("password");

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }

        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone.trim());
        }

        if (email != null && !email.isBlank()) {
            String trimmedEmail = email.trim();
            // Customers must have @gmail.com
            if (user.getRole() == User.Role.CUSTOMER) {
                if (!GMAIL_PATTERN.matcher(trimmedEmail).matches()) {
                    throw new BadRequestException("Customer email address must end with @gmail.com");
                }
            }
            // Check uniqueness if email changed
            if (!user.getEmail().equalsIgnoreCase(trimmedEmail)) {
                if (userRepository.existsByEmail(trimmedEmail)) {
                    throw new BadRequestException("Email " + trimmedEmail + " is already in use by another account.");
                }
                user.setEmail(trimmedEmail);
            }
        }

        // If password is not empty, hash with BCrypt before saving
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 4) {
                throw new BadRequestException("Password must be at least 4 characters long.");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            log.info("Password updated for user {}", user.getEmail());
        }

        User saved = userRepository.save(user);

        auditService.log("PROFILE_UPDATED",
            "User " + saved.getEmail() + " updated profile details",
            saved.getEmail(), "User", saved.getId());

        return ResponseEntity.ok(Map.of(
            "message", "Profile updated successfully",
            "id", saved.getId(),
            "fullName", saved.getFullName(),
            "email", saved.getEmail(),
            "phone", saved.getPhone(),
            "role", saved.getRole()
        ));
    }

    /**
     * PUT /api/users/{userId}/temp-password
     * Admin-only: sets temporary plain-text password, hashes with BCrypt, overrides current password
     */
    @PutMapping("/{userId}/temp-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resetTempPassword(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        String tempPassword = body.get("tempPassword");
        if (tempPassword == null || tempPassword.isBlank()) {
            tempPassword = body.get("password"); // accept either field name
        }

        if (tempPassword == null || tempPassword.isBlank()) {
            throw new BadRequestException("Temporary password is required");
        }
        if (tempPassword.length() < 4) {
            throw new BadRequestException("Password must be at least 4 characters long.");
        }

        User targetUser = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        targetUser.setPassword(passwordEncoder.encode(tempPassword));
        User saved = userRepository.save(targetUser);

        String adminEmail = userDetails != null ? userDetails.getUsername() : "ADMIN";
        auditService.log("ADMIN_TEMP_PASSWORD_RESET",
            "Admin " + adminEmail + " set temporary password for user " + saved.getEmail(),
            adminEmail, "User", saved.getId());

        return ResponseEntity.ok(Map.of(
            "message", "Temporary password successfully updated for " + saved.getEmail(),
            "userId", saved.getId(),
            "email", saved.getEmail()
        ));
    }
}
