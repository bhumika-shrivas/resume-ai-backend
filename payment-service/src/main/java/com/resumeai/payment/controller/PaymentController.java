package com.resumeai.payment.controller;

import com.resumeai.payment.dto.OrderRequest;
import com.resumeai.payment.dto.OrderResponse;
import com.resumeai.payment.dto.VerificationRequest;
import com.resumeai.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request,
                                                    @RequestHeader("loggedInUser") String userEmail) {
        return ResponseEntity.ok(paymentService.createOrder(request, userEmail));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody VerificationRequest request,
                                          @RequestHeader("loggedInUser") String userEmail) {
        boolean isValid = paymentService.verifyPayment(request, userEmail);
        if (isValid) {
            return ResponseEntity.ok(Map.of("message", "Payment verified. Premium activated!"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid payment signature"));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body,
                                      @RequestHeader("loggedInUser") String userEmail) {
        String otp = body.get("otp");
        boolean isSuccess = paymentService.verifyOtp(userEmail, otp);
        if (isSuccess) {
            return ResponseEntity.ok(Map.of("message", "Premium activated successfully"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired OTP"));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestHeader("loggedInUser") String userEmail) {
        paymentService.sendOtp(userEmail);
        return ResponseEntity.ok(Map.of("message", "OTP resent successfully"));
    }
}