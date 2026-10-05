package model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "PaymentSlips")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
public class PaymentSlip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SlipID")
    private Long id;

    /** Path on the server where the uploaded file is stored */
    @Column(name = "FilePath", nullable = false, length = 500)
    private String filePath;

    @Column(name = "OriginalFileName", length = 255)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "VerificationStatus", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "FinanceRemarks", length = 1000)
    private String financeRemarks;

    @Column(name = "UploadedAt", updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "VerifiedAt")
    private LocalDateTime verifiedAt;

    /** One payment slip belongs to one reservation */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReservationID", nullable = false, unique = true)
    @JsonIgnore
    private Reservation reservation;

    /** Finance officer who verified the slip */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VerifiedByUserID")
    private User verifiedBy;

    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED
    }
}
