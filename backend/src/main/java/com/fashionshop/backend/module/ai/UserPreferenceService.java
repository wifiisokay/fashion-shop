package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.UserPreference;
import com.fashionshop.backend.domain.repository.UserPreferenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Học sở thích người dùng từ lịch sử chat.
 * Chạy @Async — không block pipeline chính, silent fail nếu lỗi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private static final int MAX_COLOR_PREFS = 5;
    private static final int MAX_STYLE_SCORE = 10;

    private final UserPreferenceRepository repository;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    // =====================================
    // Public API
    // =====================================

    /** Lấy preferences của user (dùng cho prompt building). */
    public Optional<UserPreference> findByUserId(Long userId) {
        if (userId == null) return Optional.empty();
        return repository.findByUserId(userId);
    }

    /**
     * Format user preferences thành đoạn text ngắn nhúng vào system prompt.
     * Gemini sẽ dùng để cá nhân hoá response mà không cần user nhắc lại.
     */
    public String formatForPrompt(Long userId) {
        if (userId == null) return "";
        return repository.findByUserId(userId)
                .map(this::toPromptSnippet)
                .orElse("");
    }

    /**
     * @Async — extract và tích lũy sở thích từ message vừa gửi.
     * Silent fail: không throw exception, chỉ log warning.
     */
    @Async
    public void updateAsync(Long userId, String userMessage, String assistantResponse) {
        if (userId == null || userMessage == null || userMessage.isBlank()) return;
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> updatePreferenceInNewTransaction(userId, userMessage));
        } catch (Exception e) {
            log.warn("[USER_PREF] Update skipped for user {}: {}", userId, e.getMessage());
        }
    }

    private void updatePreferenceInNewTransaction(Long userId, String userMessage) {
        String lower = userMessage.toLowerCase(Locale.ROOT);
        UserPreference pref = repository.findByUserId(userId)
            .orElseGet(() -> UserPreference.builder()
                .userId(userId)
                .user(entityManager.getReference(User.class, userId))
                .build());

        boolean changed = false;
        changed |= learnGender(pref, lower);
        changed |= learnColor(pref, lower);
        changed |= learnStyle(pref, lower);
        changed |= learnBudget(pref, lower);
        changed |= learnSize(pref, lower);

        if (changed) {
            repository.saveAndFlush(pref);
            log.debug("Updated preferences for user {}: gender={}, colors={}, styles={}",
                    userId, pref.getGender(), pref.getColorPref(), pref.getStylePref());
        }
    }

    // =====================================
    // Private — learn methods
    // =====================================

    private boolean learnGender(UserPreference pref, String lower) {
        if (pref.getGender() != null) return false; // Không ghi đè khi đã có
        if (lower.contains(" nam") || lower.contains("nam giới") || lower.contains("cho chồng") || lower.contains("cho bạn trai")) {
            pref.setGender(Gender.MALE);
            return true;
        }
        if (lower.contains(" nữ") || lower.contains("phụ nữ") || lower.contains("cho vợ") || lower.contains("cho bạn gái") || lower.contains("con gái")) {
            pref.setGender(Gender.FEMALE);
            return true;
        }
        return false;
    }

    private boolean learnColor(UserPreference pref, String lower) {
        List<String> colors = pref.getColorPref() != null ? new ArrayList<>(pref.getColorPref()) : new ArrayList<>();
        int before = colors.size();

        Map<String, String> colorMap = Map.of(
                "đen", "Tối", "navy", "Tối", "nâu đậm", "Tối",
                "trắng", "Sáng", "be", "Trung tính", "xám", "Trung tính",
                "hồng", "Pastel", "xanh nhạt", "Pastel"
        );
        // Tông màu chung
        if (lower.contains("màu tối") || lower.contains("tông tối") || lower.contains("tone tối")) addUnique(colors, "Tối");
        if (lower.contains("màu sáng") || lower.contains("tông sáng")) addUnique(colors, "Sáng");
        if (lower.contains("trung tính") || lower.contains("neutral")) addUnique(colors, "Trung tính");
        if (lower.contains("pastel")) addUnique(colors, "Pastel");

        colorMap.forEach((keyword, family) -> {
            if (lower.contains(keyword)) addUnique(colors, family);
        });

        List<String> trimmed = colors.size() > MAX_COLOR_PREFS
                ? new ArrayList<>(colors.subList(colors.size() - MAX_COLOR_PREFS, colors.size()))
                : colors;
        pref.setColorPref(trimmed);
        return trimmed.size() != before;
    }

    private boolean learnStyle(UserPreference pref, String lower) {
        Map<String, String> styleKeywords = new LinkedHashMap<>();
        styleKeywords.put("casual", "casual-basic");
        styleKeywords.put("đi làm", "smart-casual");
        styleKeywords.put("công sở", "smart-casual");
        styleKeywords.put("smart casual", "smart-casual");
        styleKeywords.put("streetwear", "streetwear");
        styleKeywords.put("street", "streetwear");
        styleKeywords.put("thể thao", "sporty");
        styleKeywords.put("sport", "sporty");
        styleKeywords.put("dự tiệc", "formal");
        styleKeywords.put("formal", "formal");

        Map<String, Integer> scores = pref.getStylePref() != null
                ? new HashMap<>(pref.getStylePref()) : new HashMap<>();
        boolean changed = false;
        for (Map.Entry<String, String> entry : styleKeywords.entrySet()) {
            if (lower.contains(entry.getKey())) {
                String tag = entry.getValue();
                int newScore = Math.min(scores.getOrDefault(tag, 0) + 1, MAX_STYLE_SCORE);
                if (!scores.containsKey(tag) || scores.get(tag) != newScore) {
                    scores.put(tag, newScore);
                    changed = true;
                }
            }
        }
        pref.setStylePref(scores);
        return changed;
    }

    private boolean learnBudget(UserPreference pref, String lower) {
        Matcher m = Pattern.compile("(?:dưới|duoi|tầm|tam)\\s*(\\d+)\\s*(k|nghìn|ngàn|tr|triệu)?").matcher(lower);
        if (!m.find()) return false;

        long value = Long.parseLong(m.group(1));
        String unit = m.group(2);
        if (unit != null && (unit.startsWith("tr") || unit.startsWith("tri"))) {
            value *= 1_000_000;
        } else {
            value *= 1_000;
        }
        Map<String, Long> budget = pref.getBudgetRange() != null
                ? new HashMap<>(pref.getBudgetRange()) : new HashMap<>();
        long old = budget.getOrDefault("max", 0L);
        if (value != old) {
            budget.put("max", value);
            pref.setBudgetRange(budget);
            return true;
        }
        return false;
    }

    private boolean learnSize(UserPreference pref, String lower) {
        Matcher m = Pattern.compile("size\\s*(xs|s\\b|m\\b|l\\b|xl\\b|xxl\\b|\\d{2})").matcher(lower);
        if (!m.find()) return false;
        String size = m.group(1).toUpperCase();
        Map<String, String> sizeInfo = pref.getSizeInfo() != null
                ? new HashMap<>(pref.getSizeInfo()) : new HashMap<>();

        // Heuristic: nếu là số thì là bottom (waist), chữ là top
        String key = size.matches("\\d+") ? "bottom" : "top";
        if (!size.equals(sizeInfo.get(key))) {
            sizeInfo.put(key, size);
            pref.setSizeInfo(sizeInfo);
            return true;
        }
        return false;
    }

    private void addUnique(List<String> list, String value) {
        if (!list.contains(value)) list.add(value);
    }

    // =====================================
    // Format for prompt
    // =====================================

    private String toPromptSnippet(UserPreference pref) {
        StringBuilder sb = new StringBuilder();
        if (pref.getGender() != null) {
            sb.append("- Giới tính: ").append(pref.getGender() == Gender.MALE ? "Nam" : "Nữ").append("\n");
        }
        if (pref.getSizeInfo() != null && !pref.getSizeInfo().isEmpty()) {
            sb.append("- Size: ").append(pref.getSizeInfo()).append("\n");
        }
        if (pref.getColorPref() != null && !pref.getColorPref().isEmpty()) {
            sb.append("- Màu ưa thích: ").append(String.join(", ", pref.getColorPref())).append("\n");
        }
        if (pref.getStylePref() != null && !pref.getStylePref().isEmpty()) {
            // Lấy top 3 style theo score cao nhất
            pref.getStylePref().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3)
                    .forEach(e -> sb.append("- Phong cách hay hỏi: ").append(e.getKey()).append("\n"));
        }
        if (pref.getBudgetRange() != null && pref.getBudgetRange().containsKey("max")) {
            long max = pref.getBudgetRange().get("max");
            sb.append("- Ngân sách tối đa: ").append(formatVnd(max)).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatVnd(long amount) {
        if (amount >= 1_000_000) return (amount / 1_000_000) + " triệu đồng";
        return (amount / 1_000) + "k";
    }
}
