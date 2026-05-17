package com.resumeai.notification.listener;

import com.resumeai.notification.entity.Notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.resumeai.notification.dto.EmailRequest;
import com.resumeai.notification.service.NotificationService;

@Component
public class PaymentEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEmailListener.class);

    private final NotificationService notificationService;

    public PaymentEmailListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "q.payment.otp")
    public void handleOtpEmail(EmailRequest request) {
        log.info("Received OTP email request for: {}", request.getTo());
        notificationService.sendEmail(request.getTo(), request.getSubject(), request.getBody());
    }

    @RabbitListener(queues = "q.payment.success")
    public void handleSuccessEmail(EmailRequest request) {
        log.info("Received Payment Success email request for: {}", request.getTo());
        
        Notification notification = new Notification();
        notification.setRecipientId(request.getTo());
        notification.setTitle(request.getSubject());
        notification.setMessage("Your subscription payment was successful. Enjoy your premium features!");
        notification.setType("PLAN_CHANGE");
        notification.setChannel("BOTH");
        notification.setActionUrl("/app/dashboard");
        
        notificationService.send(notification);
    }
}