package com.resumeai.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String OTP_QUEUE = "q.payment.otp";
    public static final String SUCCESS_QUEUE = "q.payment.success";

    @Bean
    public Queue otpQueue() {
        return new Queue(OTP_QUEUE);
    }

    @Bean
    public Queue successQueue() {
        return new Queue(SUCCESS_QUEUE);
    }
}
