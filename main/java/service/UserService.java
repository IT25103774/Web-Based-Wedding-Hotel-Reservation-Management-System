package service;

import com.ceremonyconnect.bookingservice.dto.RegisterRequest;
import com.ceremonyconnect.bookingservice.exception.BadRequestException;
import com.ceremonyconnect.bookingservice.exception.ConflictException;
import com.ceremonyconnect.bookingservice.model.User;
import com.ceremonyconnect.bookingservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public User register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered: " + req.email());
        }

        User.Role role = User.Role.CUSTOMER;
        if (req.role() != null && !req.role().isBlank()) {
            try {
                role = User.Role.valueOf(req.role().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role: " + req.role());
            }
        }

        User user = new User();
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setPhone(req.phone());
        user.setRole(role);

        User saved = userRepository.save(user);
        auditService.log("USER_REGISTERED",
            "New user: " + saved.getEmail() + " Role: " + role,
            saved.getEmail(), "User", saved.getId());
        return saved;
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new BadRequestException("User not found: " + email));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateUserPassword(Long userId, String newPassword, String adminEmail) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadRequestException("User not found: " + userId));
        user.setPassword(passwordEncoder.encode(newPassword));
        User saved = userRepository.save(user);
        auditService.log("STAFF_PASSWORD_CHANGED",
            "Password reset for: " + saved.getEmail() + " by Admin " + adminEmail,
            adminEmail, "User", saved.getId());
        return saved;
    }

    @Transactional
    public User updateUserStatus(Long userId, boolean active, String adminEmail) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadRequestException("User not found: " + userId));
        user.setActive(active);
        User saved = userRepository.save(user);
        auditService.log("ACCOUNT_STATUS_CHANGED",
            "Account status set to " + (active ? "ACTIVE" : "DISABLED") + " for: " + saved.getEmail() + " by Admin " + adminEmail,
            adminEmail, "User", saved.getId());
        return saved;
    }
}
