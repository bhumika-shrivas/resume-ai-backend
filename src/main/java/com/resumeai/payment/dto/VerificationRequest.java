package com.resumeai.payment.dto;

import lombok.Data;

@Data
public class VerificationRequest {
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public String getRazorpaySignature() { return razorpaySignature; }
}