package com.resumeai.auth.config;

import com.resumeai.auth.entity.User;
import com.resumeai.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Autowired
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Bean
    public CommandLineRunner seedDatabase() {
        return args -> {
            if (userRepository.count() == 0) {
                System.out.println("Seeding Auth Database...");

                // Create Admin
                User admin = new User();
                admin.setFullName("Platform Admin");
                admin.setEmail("bhumikashrivas.work@gmail.com");
                admin.setPasswordHash(passwordEncoder.encode("Admin@12345"));
                admin.setRole("ADMIN");
                admin.setProvider("LOCAL");
                admin.setSubscriptionPlan("PREMIUM");
                admin.setActive(true);
                userRepository.save(admin);

                // Create Test User
                User user = new User();
                user.setFullName("John Doe");
                user.setEmail("john@example.com");
                user.setPasswordHash(passwordEncoder.encode("password123"));
                user.setRole("USER");
                user.setProvider("LOCAL");
                user.setSubscriptionPlan("FREE");
                user.setActive(true);
                userRepository.save(user);

                System.out.println("Auth Database Seeded!");
                System.out.println("Admin: bhumikashrivas.work@gmail.com / Admin@12345");
                System.out.println("User: john@example.com / password123");
            }
        };
    }
}
