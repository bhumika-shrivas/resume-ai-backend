package com.resumeai.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service")
public interface AuthClient {

    @PutMapping("/auth/internal/upgrade/{email}")
    void upgradeUserToPremium(@PathVariable("email") String email);
}
