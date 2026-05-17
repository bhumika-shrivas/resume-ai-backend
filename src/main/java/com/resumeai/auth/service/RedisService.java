package com.resumeai.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void setValue(String key, String value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            System.err.println("Redis is not available: " + e.getMessage());
        }
    }

    public String getValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            System.err.println("Redis is not available: " + e.getMessage());
            return null;
        }
    }

    public void deleteValue(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            System.err.println("Redis is not available: " + e.getMessage());
        }
    }

    public void blacklistToken(String token) {
        // Blacklist token for 24 hours
        setValue("BL_" + token, "true", 24, TimeUnit.HOURS);
    }

    public boolean isTokenBlacklisted(String token) {
        String isBlacklisted = getValue("BL_" + token);
        return "true".equals(isBlacklisted);
    }
}
