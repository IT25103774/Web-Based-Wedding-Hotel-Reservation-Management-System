package com.ceremonyconnect.bookingservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "Reservations",
    uniqueConstraints = {
        /* Conflict Detection: a hall can be booked only once per date */
        @UniqueConstraint(
            name = "uq_hall_date",
            columnNames = {"HallID", "ReservationDate"}
        )
    }
)
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReservationID")
    private Long id;

    /* ── Strict Validation 2: reservation date cannot be backdated ── */
    @NotNull(message = "Reservation date is required")
    @FutureOrPresent(message = "Reservation date must be today or in the future")
    @Column(name = "ReservationDate", nullable = false)
    private LocalDate reservationDate;

    @NotNull(message = "Number of guests is required")
    @Column(name = "GuestCount", nullable = false)
    private Integer guestCount;

    @Column(name = "CateringRequested", nullable = false)
    private boolean cateringRequested = false;

    @Column(name = "DecorationRequested", nullable = false)
    private boolean decorationRequested = false;

    @Column(name = "TotalCost", precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 30)
    private Status status = Status.PENDING;

    @Column(name = "StaffRemarks", length = 1000)
    private String staffRemarks;

    @Column(name = "SpecialRequests", length = 2000)
    private String specialRequests;

    /**
     * Customer contact email for this reservation — must be @gmail.com
     */
    @Pattern(
        regexp = "^[A-Za-z0-9._%+\\-]+@gmail\\.com$",
        message = "Contact email must be a valid @gmail.com address"
    )
    @Column(name = "ContactEmail", length = 200)
    private String contactEmail;

    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    /* ── Relationships ──────────────────────────────────────────────── */

    /** Many reservations belong to one customer */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor", "password"})
    private User customer;

    /** Many reservations use one hall */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HallID", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
    private Hall hall;

    /** Many reservations use one package */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PackageID", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
    private WeddingPackage weddingPackage;

    /** One reservation has one payment slip (One-to-One) */
    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"reservation", "hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
    private PaymentSlip paymentSlip;

    /** One reservation has one invoice (One-to-One) */
    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"reservation", "hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
    private Invoice invoice;

    /** Staff reviewer who updated this reservation */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UpdatedBy")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor", "password"})
    private User updatedBy;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        RESCHEDULED,
        PAYMENT_UPLOADED,
        CONFIRMED,
        CANCELLED
    }
}
