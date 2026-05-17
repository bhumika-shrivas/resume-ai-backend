package com.resumeai.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "auth-service")
public interface AuthClient {

    @GetMapping("/api/v1/subscription/usage/{email}")
    UserUsageDto getUsage(@PathVariable("email") String email);

    @PostMapping("/api/v1/subscription/usage/{email}/increment-ai")
    void incrementAi(@PathVariable("email") String email);

    @PostMapping("/api/v1/subscription/usage/{email}/increment-ats")
    void incrementAts(@PathVariable("email") String email);

    @GetMapping("/auth/users/email/{email}")
    UserDto getUserByEmail(@PathVariable("email") String email);

    @GetMapping("/auth/users/{id}")
    UserDto getUserById(@PathVariable("id") Long id);
}
