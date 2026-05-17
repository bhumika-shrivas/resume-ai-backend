package com.resumeai.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_pricing_config")
public class AiPricingConfig {

    @Id
    @Column(name = "model_name", nullable = false)
    private String modelName; // "GPT-4o", "CLAUDE"

    @Column(name = "cost_per_1k_tokens", nullable = false)
    private Double costPer1kTokens;

    public AiPricingConfig() {}

    public AiPricingConfig(String modelName, Double costPer1kTokens) {
        this.modelName = modelName;
        this.costPer1kTokens = costPer1kTokens;
    }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Double getCostPer1kTokens() { return costPer1kTokens; }
    public void setCostPer1kTokens(Double costPer1kTokens) { this.costPer1kTokens = costPer1kTokens; }
}
