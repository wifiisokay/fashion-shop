package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByUserIdAndSessionDate(Long userId, LocalDate date);

    Page<ChatSession> findByUserIdOrderBySessionDateDesc(Long userId, Pageable pageable);

    /** Admin: xem sessions của 1 user */
    Page<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
