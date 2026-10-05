package com.ceremonyconnect.bookingservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "WeddingPackages")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "byteBuddyInterceptor"})
public class WeddingPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PackageID")
    private Long id;

    @NotBlank
    @Column(name = "PackageName", nullable = false, unique = true)
    private String name;

    @Column(name = "Description", length = 2000)
    private String description;

    @NotNull
    @Positive
    @Column(name = "BasePrice", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    /** Catering cost per head */
    @Column(name = "CateringPricePerHead", precision = 10, scale = 2)
    private BigDecimal cateringPricePerHead;

    /** Decoration add-on cost */
    @Column(name = "DecorationPrice", precision = 12, scale = 2)
    private BigDecimal decorationPrice;

    @Column(name = "Inclusions", length = 1000)
    private String inclusions;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;
}
