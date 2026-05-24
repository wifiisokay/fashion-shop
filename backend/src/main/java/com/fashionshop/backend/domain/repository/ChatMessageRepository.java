package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /** Lấy N message gần nhất cho context (order DESC → reverse trong code) */
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);

    long countBySessionId(Long sessionId);

    /** Admin stats: phân bổ intent trong 30 ngày gần nhất */
    @Query("SELECT m.intent, COUNT(m) FROM ChatMessage m " +
           "WHERE m.intent IS NOT NULL AND m.createdAt >= :since " +
           "GROUP BY m.intent")
    List<Object[]> countByIntentSince(@Param("since") LocalDateTime since);

    /** Admin stats: số message theo ngày trong 30 ngày gần nhất */
    @Query("SELECT FUNCTION('DATE', m.createdAt), COUNT(m) FROM ChatMessage m " +
           "WHERE m.createdAt >= :since " +
           "GROUP BY FUNCTION('DATE', m.createdAt) " +
           "ORDER BY FUNCTION('DATE', m.createdAt) ASC")
    List<Object[]> countPerDaySince(@Param("since") LocalDateTime since);
}
