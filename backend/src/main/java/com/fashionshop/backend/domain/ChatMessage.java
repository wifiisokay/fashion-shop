package com.fashionshop.backend.domain;

import com.fashionshop.backend.common.enums.ChatRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tin nhắn chat AI — thuộc 1 ChatSession.
 */
@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_msg_session", columnList = "session_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(length = 30)
    private String intent;

    @Column(name = "has_products")
    @Builder.Default
    private Boolean hasProducts = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
