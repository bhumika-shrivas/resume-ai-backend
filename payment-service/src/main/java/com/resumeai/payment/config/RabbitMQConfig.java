package com.resumeai.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "payment.exchange";
    public static final String OTP_QUEUE = "q.payment.otp";
    public static final String SUCCESS_QUEUE = "q.payment.success";
    public static final String OTP_ROUTING_KEY = "payment.otp.sent";
    public static final String SUCCESS_ROUTING_KEY = "payment.success.confirmed";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue otpQueue() {
        return new Queue(OTP_QUEUE);
    }

    @Bean
    public Queue successQueue() {
        return new Queue(SUCCESS_QUEUE);
    }

    @Bean
    public Binding otpBinding(Queue otpQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(otpQueue).to(paymentExchange).with(OTP_ROUTING_KEY);
    }

    @Bean
    public Binding successBinding(Queue successQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(successQueue).to(paymentExchange).with(SUCCESS_ROUTING_KEY);
    }
}
