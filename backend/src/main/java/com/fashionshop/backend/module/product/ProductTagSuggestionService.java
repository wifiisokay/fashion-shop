package com.fashionshop.backend.module.product;

import com.fashionshop.backend.module.ai.AiClientRouter;
import com.fashionshop.backend.module.product.dto.request.ProductTagSuggestRequest;
import com.fashionshop.backend.module.product.dto.response.ProductTagSuggestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Dùng Gemini để gợi ý tag cho admin khi tạo/sửa sản phẩm.
 *
 * <p>Flow:
 * <ol>
 *   <li>Build constrained prompt với FULL tag library</li>
 *   <li>Gemini trả JSON với tag từ library</li>
 *   <li>Validate response — loại bỏ tag ngoài library</li>
 *   <li>Fallback trả empty nếu AI lỗi (không block admin)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductTagSuggestionService {

    private final AiClientRouter aiClientRouter;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_INSTRUCTION = """
            Bạn là AI hỗ trợ phân loại sản phẩm thời trang. Nhiệm vụ: đọc thông tin sản phẩm và chọn tag phù hợp.
            Quy tắc bắt buộc:
            - CHỈ chọn tag từ danh sách được cung cấp. KHÔNG tạo tag mới.
            - style_tags: chọn tối đa 4 tag (ưu tiên tag đặc trưng nhất).
            - occasion_tags: chọn tối đa 4 tag.
            - fit_type: chọn ĐÚNG 1 giá trị.
            - color_family: chọn ĐÚNG 1 giá trị.
            - Trả về JSON thuần, KHÔNG có markdown, KHÔNG có text ngoài JSON.
            - Ví dụ format: {"style_tags":["casual","minimal"],"occasion_tags":["daily","hangout"],"fit_type":"Regular","color_family":"neutral"}
            """;

    public ProductTagSuggestResponse suggestTags(ProductTagSuggestRequest request) {
        String confidence = computeConfidence(request.getDescription());
        String userPrompt = buildUserPrompt(request);

        try {
            String raw = aiClientRouter.generate(SYSTEM_INSTRUCTION, List.of(), userPrompt);
            return parseAndValidate(raw, confidence);
        } catch (Exception e) {
            log.warn("ProductTagSuggestionService: AI call failed — {}", e.getMessage());
            return ProductTagSuggestResponse.builder()
                    .confidence("NONE")
                    .message("Gợi ý tag thất bại. Vui lòng chọn tag thủ công từ danh sách bên dưới.")
                    .build();
        }
    }

    // =====================================================
    // Private helpers
    // =====================================================

    private String buildUserPrompt(ProductTagSuggestRequest req) {
        return "Tên: " + req.getName() + "\n" +
               "Mô tả: " + req.getDescription() + "\n" +
               "Giới tính: " + (req.getGender() != null ? req.getGender().name() : "không rõ") + "\n" +
               (req.getCategoryName() != null ? "Category: " + req.getCategoryName() + "\n" : "") +
               (req.getMaterial() != null ? "Chất liệu: " + req.getMaterial() + "\n" : "") +
               "\nstyle_tags hợp lệ: " + ProductTagLibrary.STYLE_TAGS.stream().sorted().toList() + "\n" +
               "occasion_tags hợp lệ: " + ProductTagLibrary.OCCASION_TAGS.stream().sorted().toList() + "\n" +
               "fit_type hợp lệ: " + ProductTagLibrary.FIT_TYPES.stream().sorted().toList() + "\n" +
               "color_family hợp lệ: " + ProductTagLibrary.COLOR_FAMILIES.stream().sorted().toList() + "\n";
    }

    private ProductTagSuggestResponse parseAndValidate(String raw, String confidence) {
        if (raw == null || raw.isBlank()) {
            return emptyResponse(confidence);
        }
        // Tách JSON khỏi markdown nếu có
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```[a-z]*\\n?", "").replace("```", "").trim();
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            List<String> styleTags = filterValid(node, "style_tags", ProductTagLibrary.STYLE_TAGS,
                    ProductTagLibrary.MAX_STYLE_TAGS);
            List<String> occasionTags = filterValid(node, "occasion_tags", ProductTagLibrary.OCCASION_TAGS,
                    ProductTagLibrary.MAX_OCCASION_TAGS);
            String fitType = validateSingle(node, "fit_type", ProductTagLibrary.FIT_TYPES);
            String colorFamily = validateSingle(node, "color_family", ProductTagLibrary.COLOR_FAMILIES);

            return ProductTagSuggestResponse.builder()
                    .styleTags(styleTags.isEmpty() ? null : styleTags)
                    .occasionTags(occasionTags.isEmpty() ? null : occasionTags)
                    .fitType(fitType)
                    .colorFamily(colorFamily)
                    .confidence(confidence)
                    .build();

        } catch (Exception e) {
            log.warn("ProductTagSuggestionService: parse failed — {}", e.getMessage());
            return emptyResponse(confidence);
        }
    }

    private List<String> filterValid(JsonNode root, String field,
                                     java.util.Set<String> validSet, int maxCount) {
        JsonNode arr = root.get(field);
        if (arr == null || !arr.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : arr) {
            String val = item.asText();
            if (validSet.contains(val) && result.size() < maxCount) {
                result.add(val);
            }
        }
        return result;
    }

    private String validateSingle(JsonNode root, String field, java.util.Set<String> validSet) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return null;
        String val = node.asText();
        return validSet.contains(val) ? val : null;
    }

    private String computeConfidence(String description) {
        if (description == null) return "NONE";
        if (description.length() >= 50) return "HIGH";
        if (description.length() >= 20) return "MEDIUM";
        return "LOW";
    }

    private ProductTagSuggestResponse emptyResponse(String confidence) {
        return ProductTagSuggestResponse.builder()
                .confidence(confidence)
                .message("AI không thể phân tích. Vui lòng chọn tag thủ công.")
                .build();
    }
}
