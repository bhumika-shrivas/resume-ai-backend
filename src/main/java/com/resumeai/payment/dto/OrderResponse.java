package com.resumeai.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private Integer amount;
    private String currency;
    private String keyId;

    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
}