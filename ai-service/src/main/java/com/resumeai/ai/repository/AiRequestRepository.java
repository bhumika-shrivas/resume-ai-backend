package com.resumeai.ai.repository;

import com.resumeai.ai.entity.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiRequestRepository extends JpaRepository<AiRequest, String> {

    List<AiRequest> findByUserId(String userId);

    List<AiRequest> findByResumeId(Long resumeId);

    List<AiRequest> findByRequestType(String requestType);

    List<AiRequest> findByStatus(String status);

    @Query("SELECT COUNT(a) FROM AiRequest a WHERE a.userId = :userId AND a.createdAt > :startOfDay")
    int countByUserIdAndCreatedAtAfter(@Param("userId") String userId, @Param("startOfDay") LocalDateTime startOfDay);

    @Query("SELECT COUNT(a) FROM AiRequest a WHERE a.userId = :userId AND a.requestType = :type AND a.createdAt > :startDate")
    int countByUserIdAndTypeAndDate(@Param("userId") String userId, @Param("type") String type, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(a.tokensUsed), 0L) FROM AiRequest a WHERE a.userId = :userId")
    Long sumTokensUsedByUserId(@Param("userId") String userId);

    @Query("SELECT COALESCE(SUM(a.tokensUsed), 0L) FROM AiRequest a")
    Long sumAllTokensUsed();

    @Query("SELECT COALESCE(SUM(a.tokensUsed), 0L) FROM AiRequest a WHERE a.model = :model")
    Long sumTokensUsedByModel(@Param("model") String model);

    @Query(value = "SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at)), 0) FROM ai_requests WHERE status = 'COMPLETED'", nativeQuery = true)
    Double getAverageQueueTime();
}
