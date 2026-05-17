package com.resumeai.payment.dto;

import lombok.Data;

@Data
public class OrderRequest {
    private Double amount; // in INR

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}