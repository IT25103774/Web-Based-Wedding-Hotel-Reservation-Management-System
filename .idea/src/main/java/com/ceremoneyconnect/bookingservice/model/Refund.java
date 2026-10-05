package com.ceremoneyconnect.bookingservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Refunds")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RefundID")
    private Long id;

    @Column(name = "RefundRef", length = 50, unique = true, nullable = false)
    private String refundRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PaymentID", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Payment payment;

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
    @Column(name = "Status", length = 20, nullable = false)
    private Status status = Status.IN_PROCESSING;

    @Column(name = "Reason", length = 500)
    private String reason;

    @Column(name = "RequestedAt", updatable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "CompletedAt")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProcessedByUserID")
    @JsonIgnoreProperties({"password", "hibernateLazyInitializer", "handler"})
    private User processedBy;

    public enum Status {
        IN_PROCESSING,
        REFUNDED
    }
}
