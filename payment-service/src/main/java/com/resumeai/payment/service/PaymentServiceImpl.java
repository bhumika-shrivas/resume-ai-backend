package com.resumeai.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.resumeai.payment.client.AuthClient;
import com.resumeai.payment.config.RabbitMQConfig;
import com.resumeai.payment.dto.*;
import com.resumeai.payment.entity.PaymentTransaction;
import com.resumeai.payment.entity.Subscription;
import com.resumeai.payment.repository.PaymentTransactionRepository;
import com.resumeai.payment.repository.SubscriptionRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final RazorpayClient razorpayClient;
    private final PaymentTransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final AuthClient authClient;

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    public PaymentServiceImpl(RazorpayClient razorpayClient,
                              PaymentTransactionRepository transactionRepository,
                              SubscriptionRepository subscriptionRepository,
                              RedisTemplate<String, Object> redisTemplate,
                              RabbitTemplate rabbitTemplate,
                              AuthClient authClient) {
        this.razorpayClient = razorpayClient;
        this.transactionRepository = transactionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.authClient = authClient;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String userEmail) {
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) (request.getAmount() * 100)); 
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            Order order = razorpayClient.orders.create(orderRequest);
            String orderId = order.get("id");

            PaymentTransaction transaction = new PaymentTransaction();
            transaction.setUserEmail(userEmail);
            transaction.setRazorpayOrderId(orderId);
            transaction.setAmount(request.getAmount());
            transaction.setCurrency("INR");
            transaction.setStatus(PaymentTransaction.PaymentStatus.CREATED);

            transactionRepository.save(transaction);

            OrderResponse response = new OrderResponse();
            response.setOrderId(orderId);
            response.setAmount((int) (request.getAmount() * 100));
            response.setCurrency("INR");
            response.setKeyId(keyId);
            return response;

        } catch (Exception e) {
            log.error("Error creating Razorpay order", e);
            throw new RuntimeException("Failed to initiate payment");
        }
    }

    @Override
    @Transactional
    public boolean verifyPayment(VerificationRequest request, String userEmail) {
        try {
            // Use Razorpay SDK's built-in signature verification
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.getRazorpayOrderId());
            options.put("razorpay_payment_id", request.getRazorpayPaymentId());
            options.put("razorpay_signature", request.getRazorpaySignature());

            boolean isValid = Utils.verifyPaymentSignature(options, keySecret);
            log.info("Razorpay signature verification result: {} for order: {}", isValid, request.getRazorpayOrderId());

            if (isValid) {
                PaymentTransaction transaction = transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                        .orElseThrow(() -> new RuntimeException("Transaction not found"));

                transaction.setRazorpayPaymentId(request.getRazorpayPaymentId());
                transaction.setRazorpaySignature(request.getRazorpaySignature());
                transaction.setStatus(PaymentTransaction.PaymentStatus.SUCCESS);
                transactionRepository.save(transaction);

                // Payment verified — directly activate Premium
                log.info("Payment verified for user: {}. Activating premium directly.", userEmail);
                activatePremium(userEmail);
                return true;
            } else {
                log.warn("Invalid Razorpay signature for order: {}", request.getRazorpayOrderId());
                return false;
            }
        } catch (Exception e) {
            log.error("Error verifying payment for user {}: {}", userEmail, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void sendOtp(String userEmail) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        redisTemplate.opsForValue().set("OTP_" + userEmail, otp, 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set("ATTEMPTS_" + userEmail, "0", 5, TimeUnit.MINUTES);

        EmailRequest emailRequest = new EmailRequest();
        emailRequest.setTo(userEmail);
        emailRequest.setSubject("ResumeAI Payment Verification");
        emailRequest.setBody("Your OTP for premium activation is: " + otp);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.OTP_ROUTING_KEY, emailRequest);
        log.info("OTP sent to queue for user: {}", userEmail);
    }

    @Override
    @Transactional
    public boolean verifyOtp(String userEmail, String otp) {
        String cachedOtp = (String) redisTemplate.opsForValue().get("OTP_" + userEmail);
        String attemptsStr = (String) redisTemplate.opsForValue().get("ATTEMPTS_" + userEmail);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= 3) {
            log.warn("Max OTP attempts reached for user: {}", userEmail);
            return false;
        }

        if (cachedOtp != null && cachedOtp.equals(otp)) {
            // Success! Upgrade user
            activatePremium(userEmail);
            redisTemplate.delete("OTP_" + userEmail);
            redisTemplate.delete("ATTEMPTS_" + userEmail);
            return true;
        } else {
            redisTemplate.opsForValue().set("ATTEMPTS_" + userEmail, String.valueOf(attempts + 1), 5, TimeUnit.MINUTES);
            return false;
        }
    }

    private void activatePremium(String userEmail) {
        // 1. Update Auth Service Role (non-critical — may fail if auth-service is down)
        try {
            authClient.upgradeUserToPremium(userEmail);
            log.info("Auth service updated to PREMIUM for user: {}", userEmail);
        } catch (Exception e) {
            log.warn("Failed to update auth service for user {} (auth-service may be down): {}. Subscription will still be saved.", userEmail, e.getMessage());
        }

        // 2. Save Subscription Record
        try {
            Subscription subscription = subscriptionRepository.findByUserEmail(userEmail)
                    .orElse(new Subscription());

            subscription.setUserEmail(userEmail);
            subscription.setPlanType("PREMIUM");
            subscription.setActive(true);
            subscription.setStartDate(LocalDateTime.now());
            subscription.setExpiryDate(null); // Lifetime

            subscriptionRepository.save(subscription);
            log.info("Subscription record saved for user: {}", userEmail);
        } catch (Exception e) {
            log.error("Failed to save subscription record for user {}: {}", userEmail, e.getMessage());
        }

        // 3. Send Confirmation Email (non-critical — don't fail if RabbitMQ is down)
        try {
            EmailRequest emailRequest = new EmailRequest();
            emailRequest.setTo(userEmail);
            emailRequest.setSubject("ResumeAI Premium Activated!");
            emailRequest.setBody("Congratulations! Your account has been upgraded to Premium. Enjoy unlimited AI generations and premium templates.");

            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.SUCCESS_ROUTING_KEY, emailRequest);
        } catch (Exception e) {
            log.warn("Failed to send confirmation email for user {} (RabbitMQ may be down): {}", userEmail, e.getMessage());
        }

        log.info("Premium activated for user: {}", userEmail);
    }

    private String generateHmacSha256(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            // Razorpay signatures are hex-encoded, NOT Base64
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating HMAC signature", e);
        }
    }
}
