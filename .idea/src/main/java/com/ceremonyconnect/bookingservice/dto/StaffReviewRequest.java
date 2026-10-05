package com.ceremonyconnect.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;

public record StaffReviewRequest(
    @NotBlank(message = "Status is required")
    String status,  // APPROVED | REJECTED | RESCHEDULED

    String remarks,
    String newDate   // ISO date string, required if status == RESCHEDULED
) {}
