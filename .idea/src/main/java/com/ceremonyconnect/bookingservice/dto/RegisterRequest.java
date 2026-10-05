package com.ceremonyconnect.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    String fullName,

    @NotBlank(message = "Email is required")
    @Pattern(
        regexp = "^[A-Za-z0-9._%+\\-]+@(gmail\\.com|ceremony\\.com)$",
        message = "Email must be a valid @gmail.com or @ceremony.com address"
    )
    String email,

    @NotBlank(message = "Password is required")
    String password,

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^(?:\\+94[\\s\\-]?)?0?[1-9][0-9\\s\\-]{7,12}$",
        message = "Please provide a valid phone number (e.g. 0771234567 or +94771234567)"
    )
    String phone,

    String role  // optional; defaults to CUSTOMER if null
) {}
