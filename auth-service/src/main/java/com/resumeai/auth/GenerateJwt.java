package com.resumeai.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GenerateJwt {
    public static void main(String[] args) {
        String secret = "4567890qwertyuiopasdfghjklzxcvbnm1234567890qwertyuiopasdfghjklzxcvbnm";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "USER");
        claims.put("plan", "FREE");
        
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject("aryaamishra0001@gmail.com")
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
                
        System.out.println(token);
    }
}