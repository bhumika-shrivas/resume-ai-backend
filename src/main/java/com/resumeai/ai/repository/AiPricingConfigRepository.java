package com.resumeai.ai.repository;

import com.resumeai.ai.entity.AiPricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiPricingConfigRepository extends JpaRepository<AiPricingConfig, String> {
}
