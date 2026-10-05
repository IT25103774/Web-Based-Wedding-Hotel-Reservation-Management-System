package controller;

import com.ceremonyconnect.bookingservice.model.Payment;
import com.ceremonyconnect.bookingservice.model.Refund;
import com.ceremonyconnect.bookingservice.model.User;
import com.ceremonyconnect.bookingservice.repository.UserRepository;
import com.ceremonyconnect.bookingservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/payments", "/api/finance"})
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    /**
     * POST /api/payments/upload
     * Customer uploads bank transfer slip (Advance or Full)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPayment(
            @RequestParam("reservationId") Long reservationId,
            @RequestParam(value = "amount", required = false) BigDecimal amount,
            @RequestParam(value = "paymentType", defaultValue = "ADVANCE") String paymentTypeStr,
            @RequestParam(value = "bankName", required = false) String bankName,
            @RequestParam(value = "transactionRef", required = false) String transactionRef,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        Payment.PaymentType type = Payment.PaymentType.ADVANCE;
        try {
            type = Payment.PaymentType.valueOf(paymentTypeStr.toUpperCase());
        } catch (Exception ignored) {}

        Payment payment = paymentService.uploadPayment(
            reservationId, amount, type, bankName, transactionRef, file, userDetails.getUsername());

        return ResponseEntity.ok(Map.of(
            "message", "Payment slip uploaded successfully",
            "paymentId", payment.getId(),
            "paymentRef", payment.getPaymentRef(),
            "status", payment.getStatus()
        ));
    }

    /**
     * POST /api/payments/upload/{reservationId}
     * Backward-compatible endpoint for existing dashboard calls
     */
    @PostMapping(value = "/upload/{reservationId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPaymentById(
            @PathVariable Long reservationId,
            @RequestParam(value = "amount", required = false) BigDecimal amount,
            @RequestParam(value = "paymentType", defaultValue = "ADVANCE") String paymentTypeStr,
            @RequestParam(value = "bankName", required = false) String bankName,
            @RequestParam(value = "transactionRef", required = false) String transactionRef,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        return uploadPayment(reservationId, amount, paymentTypeStr, bankName, transactionRef, file, userDetails);
    }

    /**
     * GET /api/payments/my-summary (or /customer-summary)
     * Returns Advance Required, Total Paid, Outstanding Balance, Active Booking & Payment History
     */
    @GetMapping({"/my-summary", "/customer-summary"})
    public ResponseEntity<?> getCustomerSummary(@AuthenticationPrincipal UserDetails userDetails) {
        User customer = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("Customer not found"));

        return ResponseEntity.ok(paymentService.getCustomerPaymentSummary(customer.getId()));
    }

    /**
     * GET /api/payments/verification-queue (or /pending-slips)
     * Finance Officer: Pending slips awaiting verification
     */
    @GetMapping({"/verification-queue", "/pending-slips"})
    public List<Payment> getVerificationQueue() {
        return paymentService.getVerificationQueue();
    }

    /**
     * POST /api/payments/{id}/verify
     * Finance Officer verifies payment slip
     */
    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyPayment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User officer = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("Officer not found"));

        String remarks = (body != null) ? body.getOrDefault("remarks", "Payment slip verified successfully") : "Verified";
        Payment verified = paymentService.verifyPayment(id, remarks, officer.getId());

        return ResponseEntity.ok(Map.of(
            "message", "Payment verified successfully",
            "paymentRef", verified.getPaymentRef(),
            "status", verified.getStatus()
        ));
    }

    /**
     * POST /api/payments/{id}/reject
     * Finance Officer rejects payment slip with reason
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectPayment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User officer = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("Officer not found"));

        String reason = body.getOrDefault("reason", "Payment slip details mismatched or unreadable.");
        Payment rejected = paymentService.rejectPayment(id, reason, officer.getId());

        return ResponseEntity.ok(Map.of(
            "message", "Payment slip rejected",
            "paymentRef", rejected.getPaymentRef(),
            "status", rejected.getStatus(),
            "reason", rejected.getRejectionReason()
        ));
    }

    /**
     * GET /api/payments/transactions
     * Finance Officer: All financial transactions
     */
    @GetMapping("/transactions")
    public List<Payment> getAllTransactions() {
        return paymentService.getAllTransactions();
    }

    /**
     * GET /api/payments/refunds
     * Finance Officer: All refund records (IN_PROCESSING and REFUNDED)
     */
    @GetMapping("/refunds")
    public List<Refund> getRefunds() {
        return paymentService.getRefunds();
    }

    /**
     * POST /api/payments/refunds/{id}/complete
     * Finance Officer marks refund as completed / REFUNDED
     */
    @PostMapping("/refunds/{id}/complete")
    public ResponseEntity<?> completeRefund(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User officer = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("Officer not found"));

        Refund completed = paymentService.completeRefund(id, officer.getId());

        return ResponseEntity.ok(Map.of(
            "message", "Refund marked as completed",
            "refundRef", completed.getRefundRef(),
            "status", completed.getStatus()
        ));
    }

    /**
     * GET /api/payments/file/{id}
     * Both Dashboards: View/stream uploaded slip image or PDF
     */
    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> viewSlipFile(@PathVariable Long id) throws IOException {
        File file = paymentService.getPaymentSlipFile(id);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String contentType = Files.probeContentType(file.toPath());
        if (contentType == null) contentType = "image/jpeg";

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
            .body(resource);
    }
}
