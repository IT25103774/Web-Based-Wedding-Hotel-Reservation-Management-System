package model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Invoices")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "InvoiceID")
    private Long id;

    @Column(name = "InvoiceNumber", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "Subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "TaxRate", precision = 5, scale = 4)
    private BigDecimal taxRate = new BigDecimal("0.10");

    @Column(name = "TaxAmount", precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "TotalAmount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "GeneratedAt", updatable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentStatus", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PAID;

    /** One invoice belongs to one reservation (One-to-One) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReservationID", nullable = false, unique = true)
    @JsonIgnore
    private Reservation reservation;

    /** Finance officer who generated this invoice */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GeneratedByUserID")
    private User generatedBy;

    public enum PaymentStatus {
        PAID, PARTIALLY_PAID, REFUNDED
    }
}
