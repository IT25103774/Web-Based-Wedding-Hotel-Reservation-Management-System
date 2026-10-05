package com.ceremonyconnect.bookingservice.controller;

import com.ceremonyconnect.bookingservice.model.Invoice;
import com.ceremonyconnect.bookingservice.model.PaymentSlip;
import com.ceremonyconnect.bookingservice.repository.UserRepository;
import com.ceremonyconnect.bookingservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/finance", "/api/payments"})
@RequiredArgsConstructor
public class FinanceController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    /**
     * POST /api/finance/upload/{reservationId}
     * Customer uploads payment slip (multipart/form-data)
     */
    @PostMapping("/upload/{reservationId}")
    public ResponseEntity<?> uploadSlip(
            @PathVariable Long reservationId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        PaymentSlip slip = paymentService.uploadPaymentSlip(
            reservationId, file, userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
            "message", "Payment slip uploaded successfully",
            "slipId", slip.getId(),
            "status", slip.getVerificationStatus()
        ));
    }

    /**
     * GET /api/finance/pending-slips
     * Finance officer views all pending payment slips
     */
    @GetMapping("/pending-slips")
    public List<PaymentSlip> getPendingSlips() {
        return paymentService.getPendingSlips();
    }

    /**
     * POST /api/finance/verify/{slipId}
     * Finance officer verifies slip and generates invoice
     */
    @PostMapping("/verify/{slipId}")
    public ResponseEntity<?> verifySlip(
            @PathVariable Long slipId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        boolean approved = Boolean.parseBoolean(body.getOrDefault("approved", true).toString());
        String remarks = body.getOrDefault("remarks", "").toString();

        Long officerId = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow().getId();

        Invoice invoice = paymentService.verifyAndGenerateInvoice(slipId, approved, remarks, officerId);

        if (invoice == null) {
            return ResponseEntity.ok(Map.of("message", "Payment slip rejected"));
        }
        return ResponseEntity.ok(Map.of(
            "message", "Invoice generated successfully",
            "invoiceNumber", invoice.getInvoiceNumber(),
            "totalAmount", invoice.getTotalAmount(),
            "taxAmount", invoice.getTaxAmount()
        ));
    }
}
