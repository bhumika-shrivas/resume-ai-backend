package com.resumeai.payment.service;

import com.resumeai.payment.dto.OrderRequest;
import com.resumeai.payment.dto.OrderResponse;
import com.resumeai.payment.dto.VerificationRequest;

public interface PaymentService {
    OrderResponse createOrder(OrderRequest request, String userEmail);
    boolean verifyPayment(VerificationRequest request, String userEmail);
    void sendOtp(String userEmail);
    boolean verifyOtp(String userEmail, String otp);
}
