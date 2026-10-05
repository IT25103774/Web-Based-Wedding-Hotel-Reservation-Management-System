package model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Users")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserID")
    private Long id;

    @NotBlank(message = "Full name is required")
    @Column(name = "FullName", nullable = false, length = 150)
    private String fullName;

    /**
     * Strict Validation 1: email MUST end with @gmail.com (customers) or @ceremony.com (staff)
     */
    @NotBlank(message = "Email is required")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+\\-]+@(gmail\\.com|ceremony\\.com)$",
            message = "Email must be a valid @gmail.com or @ceremony.com address"
    )
    @Column(name = "Email", nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Column(name = "PasswordHash", nullable = false, length = 512)
    @JsonIgnore
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^(?:\\+94[\\s\\-]?)?0?[1-9][0-9\\s\\-]{7,12}$",
        message = "Please provide a valid phone number (e.g. 0771234567 or +94771234567)"
    )
    @Column(name = "Phone", nullable = false, length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "UserRole", nullable = false, length = 50)
    private Role role = Role.CUSTOMER;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;

    public enum Role {
        CUSTOMER,
        FRONT_OFFICE_SUPERVISOR,
        FINANCE_OFFICER,
        EVENT_COORDINATOR,
        CUSTOMER_RELATIONS_EXECUTIVE,
        ADMIN
    }
}