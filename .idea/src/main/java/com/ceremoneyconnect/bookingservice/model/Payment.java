package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Payments")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentID")
    private Long id;

    @Column(name = "PaymentRef", length = 50, unique = true, nullable = false)
    private String paymentRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReservationID", nullable = false)
    @JsonIgnoreProperties({"paymentSlip", "invoice", "hibernateLazyInitializer", "handler"})
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID", nullable = false)
    @JsonIgnoreProperties({"password", "hibernateLazyInitializer", "handler"})
    private User customer;

    @Column(name = "Amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentType", length = 20, nullable = false)
    private PaymentType paymentType = PaymentType.ADVANCE;

    @Column(name = "BankName", length = 150)
    private String bankName;

    @Column(name = "TransactionReference", length = 150)
    private String transactionReference;

    @Column(name = "FilePath", length = 500)
    private String filePath;

    @Column(name = "OriginalFileName", length = 255)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20, nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "RejectionReason", length = 1000)
    private String rejectionReason;

    @Column(name = "SubmittedAt", updatable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "VerifiedAt")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VerifiedByUserID")
    @JsonIgnoreProperties({"password", "hibernateLazyInitializer", "handler"})
    private User verifiedBy;

    public enum PaymentType {
        ADVANCE,
        FULL
    }

    public enum Status {
        PENDING,
        VERIFIED,
        REJECTED,
        REFUNDED
    }
}
