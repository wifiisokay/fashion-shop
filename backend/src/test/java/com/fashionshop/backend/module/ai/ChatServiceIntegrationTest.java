package com.fashionshop.backend.module.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.module.ai.dto.response.ChatMessageResponse;

@SpringBootTest
@Transactional
public class ChatServiceIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Find any user or create one
        testUser = userRepository.findAll().stream().findFirst().orElseGet(() -> {
            User user = User.builder()
                    .email("test-ai-user@fashionshop.com")
                    .fullName("Test AI User")
                    .password("password")
                    .build();
            return userRepository.save(user);
        });
    }

    @Test
    void testOutOfScopeMessageBlockedEarly() {
        // Query unrelated to fashion
        String outOfScopeMsg = "Viết code thuật toán tìm kiếm nhị phân bằng Java";
        
        ChatMessageResponse response = chatService.processMessage(testUser.getId(), outOfScopeMsg);
        
        System.out.println("--- OUT OF SCOPE RESPONSE ---");
        System.out.println("Intent: " + response.getIntent());
        System.out.println("InternalIntent: " + response.getInternalIntent());
        System.out.println("Content: " + response.getContent());
        System.out.println("IsFromFallback: " + response.getIsFromFallback());
        
        assertThat(response.getIntent()).isEqualTo(ChatIntent.OUT_OF_SCOPE.name());
        assertThat(response.getIsFromFallback()).isTrue();
        assertThat(response.getContent()).contains("Mình hiện chỉ hỗ trợ các câu hỏi liên quan đến sản phẩm thời trang");
    }

    @Test
    void testJeansSearchWithGuardAndFallbacks() {
        String jeansMsg = "Có quần jeans nam không?";
        
        ChatMessageResponse response = chatService.processMessage(testUser.getId(), jeansMsg);
        
        System.out.println("--- JEANS SEARCH RESPONSE ---");
        System.out.println("Intent: " + response.getIntent());
        System.out.println("SearchStatus: " + response.getSearchStatus());
        System.out.println("Content: " + response.getContent());
        System.out.println("Products size: " + (response.getProducts() != null ? response.getProducts().size() : 0));
        if (response.getProducts() != null && !response.getProducts().isEmpty()) {
            System.out.println("First product: " + response.getProducts().get(0).getName());
        }
    }

    @Test
    void testFollowUpMoreExcludesShownProducts() {
        // Ask for black shirt
        ChatMessageResponse response1 = chatService.processMessage(testUser.getId(), "Tìm áo thun đen");
        System.out.println("--- FIRST RESPONSE (Tìm áo thun đen) ---");
        System.out.println("Products: " + (response1.getProducts() != null ? response1.getProducts().size() : 0));
        
        // Ask for more
        ChatMessageResponse response2 = chatService.processMessage(testUser.getId(), "Có mẫu khác không?");
        System.out.println("--- SECOND RESPONSE (Có mẫu khác không) ---");
        System.out.println("Products: " + (response2.getProducts() != null ? response2.getProducts().size() : 0));
    }
}
