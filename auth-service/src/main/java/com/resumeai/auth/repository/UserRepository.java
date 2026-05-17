package com.resumeai.auth.repository;

import com.resumeai.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    
    // findByUserId is essentially findById which is provided by JpaRepository
    
    boolean existsByEmail(String email);
    
    List<User> findAllByRole(String role);
    
    List<User> findBySubscriptionPlan(String plan);
    
    List<User> findByIsActive(boolean isActive);
    
    void deleteById(Long id); // Maps to deleteByUserId
}
