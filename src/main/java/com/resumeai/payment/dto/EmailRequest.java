package com.resumeai.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {
    private String to;
    private String subject;
    private String body;

    public void setTo(String to) { this.to = to; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setBody(String body) { this.body = body; }
}