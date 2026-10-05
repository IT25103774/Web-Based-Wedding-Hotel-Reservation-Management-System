package controller;

import com.ceremonyconnect.bookingservice.dto.LoginRequest;
import com.ceremonyconnect.bookingservice.dto.RegisterRequest;
import com.ceremonyconnect.bookingservice.model.User;
import com.ceremonyconnect.bookingservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    /** POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        User created = userService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "Registration successful",
            "userId", created.getId(),
            "email", created.getEmail(),
            "role", created.getRole().name()
        ));
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));

            User user = userService.findByEmail(req.email());
            return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "userId", user.getId(),
                "fullName", user.getFullName()
            ));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid email or password"));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Account has been deactivated. Please contact the administrator."));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Authentication failed"));
        }
    }
}
