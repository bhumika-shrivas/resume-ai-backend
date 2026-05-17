package com.resumeai.export.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthClient {
    @GetMapping("/auth/users/email/{email}")
    Map<String, Object> getUserByEmail(@PathVariable("email") String email);
}
