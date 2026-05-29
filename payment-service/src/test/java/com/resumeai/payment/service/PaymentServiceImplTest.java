package com.resumeai.payment.service;

import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.resumeai.payment.client.AuthClient;
import com.resumeai.payment.config.RabbitMQConfig;
import com.resumeai.payment.dto.EmailRequest;
import com.resumeai.payment.dto.OrderRequest;
import com.resumeai.payment.dto.OrderResponse;
import com.resumeai.payment.dto.VerificationRequest;
import com.resumeai.payment.entity.PaymentTransaction;
import com.resumeai.payment.entity.Subscription;
import com.resumeai.payment.repository.PaymentTransactionRepository;
import com.resumeai.payment.repository.SubscriptionRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentServiceImpl.
 * 
 * Testing Focus:
 *  - Integration with Razorpay SDK (Order Creation & Signature Verification)
 *  - Graceful degradation: Premium activation shouldn't fail if Auth-Service or RabbitMQ is down.
 *  - OTP Verification flow and cache limiting.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private OrderClient orderClient; // Sub-client of RazorpayClient

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AuthClient authClient;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static MockedStatic<Utils> utilsMockedStatic;

    @BeforeAll
    static void initStaticMocks() {
        // Mock static utility methods from Razorpay SDK
        utilsMockedStatic = mockStatic(Utils.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        utilsMockedStatic.close();
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "keyId", "test_key_id");
        ReflectionTestUtils.setField(paymentService, "keySecret", "test_key_secret");

        // RazorpayClient uses public fields for sub-clients, so we must set it via reflection
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);
    }

    // ========================================================================
    //  createOrder() Tests
    // ========================================================================
    @Nested
    @DisplayName("createOrder()")
    class CreateOrderTests {

        @Test
        @DisplayName("Should create Razorpay order and save transaction")
        void createOrder_success() throws Exception {
            // ARRANGE
            OrderRequest request = new OrderRequest();
            request.setAmount(199.0);

            // Mock Razorpay Order response
            Order mockOrder = mock(Order.class);
            when(mockOrder.get("id")).thenReturn("order_abc123");
            when(orderClient.create(any(JSONObject.class))).thenReturn(mockOrder);

            // Mock saving to DB
            when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            OrderResponse response = paymentService.createOrder(request, "user@test.com");

            // ASSERT
            assertEquals("order_abc123", response.getOrderId());
            assertEquals(19900, response.getAmount()); // Converted to paise
            assertEquals("test_key_id", response.getKeyId());

            verify(transactionRepository).save(any(PaymentTransaction.class));
        }

        @Test
        @DisplayName("Should throw exception if Razorpay throws an error")
        void createOrder_razorpayFailure() throws Exception {
            OrderRequest request = new OrderRequest();
            request.setAmount(199.0);

            // Razorpay throws exception
            when(orderClient.create(any(JSONObject.class))).thenThrow(new com.razorpay.RazorpayException("API Error"));

            RuntimeException ex = assertThrows(RuntimeException.class, 
                () -> paymentService.createOrder(request, "user@test.com"));
            assertTrue(ex.getMessage().contains("Failed to initiate payment"));
        }
    }

    // ========================================================================
    //  verifyPayment() Tests
    // ========================================================================
    @Nested
    @DisplayName("verifyPayment()")
    class VerifyPaymentTests {

        @Test
        @DisplayName("Should return true and activate premium when signature is valid")
        void verifyPayment_validSignature() throws Exception {
            // ARRANGE
            VerificationRequest request = new VerificationRequest();
            request.setRazorpayOrderId("order_123");
            request.setRazorpayPaymentId("pay_123");
            request.setRazorpaySignature("sig_123");

            // Mock static Razorpay Utils to return true
            utilsMockedStatic.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("test_key_secret")))
                             .thenReturn(true);

            PaymentTransaction tx = new PaymentTransaction();
            when(transactionRepository.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(tx));
            when(subscriptionRepository.findByUserEmail("user@test.com")).thenReturn(Optional.empty());

            // ACT
            boolean result = paymentService.verifyPayment(request, "user@test.com");

            // ASSERT
            assertTrue(result);
            assertEquals(PaymentTransaction.PaymentStatus.SUCCESS, tx.getStatus());
            
            // Verifying Premium Activation sub-tasks:
            verify(authClient).upgradeUserToPremium("user@test.com");
            verify(subscriptionRepository).save(any(Subscription.class));
            verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.SUCCESS_ROUTING_KEY), any(EmailRequest.class));
        }

        @Test
        @DisplayName("Should return false when signature is invalid")
        void verifyPayment_invalidSignature() throws Exception {
            VerificationRequest request = new VerificationRequest();
            
            // Mock static Razorpay Utils to return FALSE
            utilsMockedStatic.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                             .thenReturn(false);

            boolean result = paymentService.verifyPayment(request, "user@test.com");

            assertFalse(result);
            verify(transactionRepository, never()).save(any(PaymentTransaction.class));
        }
    }

    // ========================================================================
    //  OTP Flow Tests
    // ========================================================================
    @Nested
    @DisplayName("OTP Flow")
    class OtpTests {

        @BeforeEach
        void setupRedisOps() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("sendOtp should store OTP and attempts in Redis and queue email")
        void sendOtp_success() {
            // ACT
            paymentService.sendOtp("user@test.com");

            // VERIFY: Redis saves OTP and Attempts
            verify(valueOperations).set(eq("OTP_user@test.com"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
            verify(valueOperations).set(eq("ATTEMPTS_user@test.com"), eq("0"), eq(5L), eq(TimeUnit.MINUTES));
            
            // VERIFY: RabbitMQ queues email
            verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.OTP_ROUTING_KEY), any(EmailRequest.class));
        }

        @Test
        @DisplayName("verifyOtp should activate premium on success")
        void verifyOtp_success() {
            when(valueOperations.get("OTP_user@test.com")).thenReturn("123456");
            when(valueOperations.get("ATTEMPTS_user@test.com")).thenReturn("0");
            when(subscriptionRepository.findByUserEmail("user@test.com")).thenReturn(Optional.empty());

            // ACT
            boolean result = paymentService.verifyOtp("user@test.com", "123456");

            // ASSERT
            assertTrue(result);
            verify(authClient).upgradeUserToPremium("user@test.com");
            
            // Cache cleanup
            verify(redisTemplate).delete("OTP_user@test.com");
            verify(redisTemplate).delete("ATTEMPTS_user@test.com");
        }

        @Test
        @DisplayName("verifyOtp should block after 3 failed attempts")
        void verifyOtp_maxAttemptsReached() {
            when(valueOperations.get("OTP_user@test.com")).thenReturn("123456");
            when(valueOperations.get("ATTEMPTS_user@test.com")).thenReturn("3"); // 3 attempts already!

            // ACT
            boolean result = paymentService.verifyOtp("user@test.com", "wrong");

            // ASSERT
            assertFalse(result);
            verify(authClient, never()).upgradeUserToPremium(anyString());
        }
    }

    // ========================================================================
    //  activatePremium Graceful Degradation Tests
    // ========================================================================
    @Nested
    @DisplayName("Premium Activation Fault Tolerance")
    class ActivatePremiumFaultToleranceTests {

        @Test
        @DisplayName("Should still save subscription even if auth-service fails")
        void activatePremium_authServiceDown_stillSavesSubscription() {
            // This test focuses on the private activatePremium() method by calling verifyOtp() on success
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("OTP_user@test.com")).thenReturn("123456");
            when(subscriptionRepository.findByUserEmail("user@test.com")).thenReturn(Optional.empty());

            // Simulate auth-service being down
            doThrow(new RuntimeException("Auth service down")).when(authClient).upgradeUserToPremium(anyString());

            // ACT: This internally calls activatePremium
            boolean result = paymentService.verifyOtp("user@test.com", "123456");

            // ASSERT: Still succeeded, and subscription was STILL saved despite auth-service failure
            assertTrue(result);
            verify(subscriptionRepository).save(any(Subscription.class));
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(EmailRequest.class));
        }

        @Test
        @DisplayName("Should still save subscription even if RabbitMQ fails")
        void activatePremium_rabbitMqDown_stillSavesSubscription() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("OTP_user@test.com")).thenReturn("123456");
            when(subscriptionRepository.findByUserEmail("user@test.com")).thenReturn(Optional.empty());

            // Simulate RabbitMQ being down
            doThrow(new RuntimeException("Rabbit MQ down")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

            // ACT
            boolean result = paymentService.verifyOtp("user@test.com", "123456");

            // ASSERT
            assertTrue(result);
            verify(authClient).upgradeUserToPremium("user@test.com");
            verify(subscriptionRepository).save(any(Subscription.class));
        }
    }
}
