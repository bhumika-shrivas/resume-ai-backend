package com.resumeai.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ResumeAiWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResumeAiWebApplication.class, args);
    }
}
