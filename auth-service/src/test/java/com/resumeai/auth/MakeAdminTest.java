package com.resumeai.auth;

import com.resumeai.auth.entity.User;
import com.resumeai.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MakeAdminTest {

    @Autowired
    private UserRepository repo;

    @Test
    public void makeAdmin() {
        repo.findByEmail("bhumikashrivas.work@gmail.com").ifPresent(u -> {
            u.setRole("ADMIN");
            u.setSubscriptionPlan("PREMIUM");
            repo.save(u);
            System.out.println("USER UPDATED SUCCESSFULLY");
        });
    }
}
