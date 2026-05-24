package com.fashionshop.backend.module.ai;


import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.domain.*;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.JoinType;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Truy vấn DB theo intent → trả về context text để nhúng vào prompt Gemini.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetrieverService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;

    /**
     * Retrieve context data dựa trên intent.
     *
     * @param intent  intent đã classify
     * @param message tin nhắn gốc (dùng để extract params)
     * @param userId  ID user (null nếu guest)
     * @return context text để nhúng vào prompt
     */
    public String retrieveContext(ChatIntent intent, String message, Long userId) {
        return switch (intent) {
            case PRODUCT_SEARCH -> retrieveProducts(message);
            case OUTFIT_SUGGEST -> retrieveForOutfit(message);
            case ORDER_INQUIRY -> retrieveOrders(userId);
            case RETURN_SUPPORT -> retrieveReturns(userId);
            case GENERAL_SUPPORT, CHITCHAT -> ""; // Handled by static system prompt
        };
    }

    public ProductSearchResult retrieveProductSearch(String message) {
        try {
            String lowerMsg = message == null ? "" : message.toLowerCase();
            Specification<Product> spec = buildProductSpec(lowerMsg);

            long total = productRepository.count(spec);
            if (total == 0) {
                return new ProductSearchResult(0, List.of());
            }

            List<Product> candidates = productRepository.findAll(spec, PageRequest.of(0, 30)).getContent();
            if (candidates.isEmpty()) {
                return new ProductSearchResult(0, List.of());
            }

            Collections.shuffle(candidates, new Random());
            List<Product> top3 = candidates.stream().limit(3).collect(Collectors.toList());
            return new ProductSearchResult(total, top3);
        } catch (Exception e) {
            log.error("Error retrieving product search: {}", e.getMessage());
            return new ProductSearchResult(0, List.of());
        }
    }

    private String retrieveProducts(String message) {
        try {
            String lowerMsg = message == null ? "" : message.toLowerCase();
            Specification<Product> spec = buildProductSpec(lowerMsg);

            long total = productRepository.count(spec);
            List<Product> candidates = productRepository.findAll(spec, PageRequest.of(0, 30)).getContent();

            if (total == 0 || candidates.isEmpty()) {
                return "Total matched: 0\nNo products found.";
            }

            Collections.shuffle(candidates, new Random());
            List<Product> top3 = candidates.stream().limit(3).collect(Collectors.toList());

            return "Total matched: " + total + "\n" +
                "Top 3 products:\n" +
                top3.stream().map(this::formatProduct).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Error retrieving products: {}", e.getMessage());
            return "";
        }
    }

    private String retrieveForOutfit(String message) {
        try {
            // Query diverse products for outfit suggestion
            List<Product> products = productRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("status"), ProductStatus.ACTIVE),
                PageRequest.of(0, 15)
            ).getContent();

            if (products.isEmpty()) return "";

            return "Danh sách sản phẩm có sẵn trong shop để gợi ý phối đồ:\n" +
                products.stream().map(this::formatProduct).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Error retrieving outfit products: {}", e.getMessage());
            return "";
        }
    }

    private String retrieveOrders(Long userId) {
        if (userId == null) return "";

        try {
            var orders = orderRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 3)
            ).getContent();

            if (orders.isEmpty()) {
                return "Khách hàng chưa có đơn hàng nào.";
            }

            return "Đơn hàng gần nhất của khách hàng:\n" +
                orders.stream().map(this::formatOrder).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Error retrieving orders: {}", e.getMessage());
            return "";
        }
    }

    private String retrieveReturns(Long userId) {
        if (userId == null) return "";

        try {
            var returns = returnRequestRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 3)
            ).getContent();

            if (returns.isEmpty()) {
                return "Khách hàng chưa có yêu cầu đổi/trả hoặc khiếu nại nào.";
            }

            return "Yêu cầu đổi/trả hoặc khiếu nại gần nhất:\n" +
                returns.stream().map(this::formatReturn).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Error retrieving returns: {}", e.getMessage());
            return "";
        }
    }

    // === Format helpers ===

    private String formatProduct(Product p) {
        String price = p.getIsSale() && p.getSalePrice() != null
            ? String.format("%s (giảm từ %s)", p.getSalePrice(), p.getBasePrice())
            : p.getBasePrice().toString();

        String primaryImage = "";
        if (p.getImages() != null && !p.getImages().isEmpty()) {
            primaryImage = p.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(p.getImages().get(0).getImageUrl());
        }

        return String.format("- [ID:%d] %s | Giá: %s VNĐ | Giới tính: %s | Chất liệu: %s | Màu: %s | Image: %s",
            p.getId(), p.getName(), price, p.getGender(),
            p.getMaterial() != null ? p.getMaterial() : "N/A",
            primaryColorFamily(p),
            primaryImage);
    }

    private String primaryColorFamily(Product product) {
        if (product.getColors() == null || product.getColors().isEmpty()) {
            return "N/A";
        }
        return product.getColors().stream()
            .min(Comparator
                .comparing((ProductColor color) -> color.getDisplayOrder() != null ? color.getDisplayOrder() : 0)
                .thenComparing(color -> color.getId() != null ? color.getId() : Long.MAX_VALUE))
            .map(ProductColor::getColorFamily)
            .filter(value -> value != null && !value.isBlank())
            .orElse("N/A");
    }

    private String formatOrder(Order o) {
        String items = o.getItems() != null
            ? o.getItems().stream()
                .map(i -> i.getProductName() + " x" + i.getQuantity())
                .collect(Collectors.joining(", "))
            : "N/A";

        return String.format("- Đơn #%d | Trạng thái: %s | Tổng: %s VNĐ | Sản phẩm: %s | Ngày đặt: %s",
            o.getId(), o.getStatus(), o.getTotalAmount(), items,
            o.getCreatedAt() != null ? o.getCreatedAt().toLocalDate() : "N/A");
    }

    private String formatReturn(ReturnRequest r) {
        return String.format("- Return #%d (Đơn #%d) | Trạng thái: %s | Lý do: %s | Ngày tạo: %s",
            r.getId(),
            r.getOrder() != null ? r.getOrder().getId() : 0,
            r.getStatus(),
            r.getReason(),
            r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : "N/A");
    }

    private Specification<Product> buildProductSpec(String lowerMsg) {
        Specification<Product> spec = Specification.where(
            (root, query, cb) -> cb.equal(root.get("status"), ProductStatus.ACTIVE)
        );

        List<Integer> categoryIds = extractCategoryIds(lowerMsg);
        if (!categoryIds.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("category").get("id").in(categoryIds));
        }

        List<String> typeKeywords = List.of(
            "quần", "áo", "váy", "đầm", "giày", "dép", "túi", "mũ", "nón", "kính",
            "short", "shorts", "jean", "jeans", "kaki", "hoodie", "sơ mi", "thun"
        );
        List<String> matchedTypes = typeKeywords.stream()
            .filter(lowerMsg::contains)
            .collect(Collectors.toList());
        if (!matchedTypes.isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                var categoryJoin = root.join("category", JoinType.LEFT);
                var predicates = matchedTypes.stream()
                    .map(keyword -> {
                        String pattern = "%" + keyword + "%";
                        return cb.or(
                            cb.like(cb.lower(root.get("name")), pattern),
                            cb.like(cb.lower(categoryJoin.get("name")), pattern)
                        );
                    })
                    .toArray(jakarta.persistence.criteria.Predicate[]::new);
                return cb.or(predicates);
            });
        }

        if (lowerMsg.contains("nam")) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("gender"), com.fashionshop.backend.common.enums.Gender.MALE));
        } else if (lowerMsg.contains("nữ")) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("gender"), com.fashionshop.backend.common.enums.Gender.FEMALE));
        }

        if (lowerMsg.contains("sale") || lowerMsg.contains("giảm giá") || lowerMsg.contains("khuyến mãi")) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isSale"), true));
        }

        return spec;
    }

    private List<Integer> extractCategoryIds(String lowerMsg) {
        if (lowerMsg == null || lowerMsg.isBlank()) {
            return List.of();
        }

        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<Category>> childrenMap = categories.stream()
            .filter(cat -> cat.getParent() != null)
            .collect(Collectors.groupingBy(cat -> cat.getParent().getId()));

        List<Integer> matched = new java.util.ArrayList<>();
        for (Category category : categories) {
            String name = category.getName() != null ? category.getName().toLowerCase() : "";
            String slug = category.getSlug() != null ? category.getSlug().toLowerCase().replace('-', ' ') : "";
            if ((!name.isBlank() && lowerMsg.contains(name)) || (!slug.isBlank() && lowerMsg.contains(slug))) {
                matched.add(category.getId());
                List<Category> children = childrenMap.get(category.getId());
                if (children != null) {
                    children.forEach(child -> matched.add(child.getId()));
                }
            }
        }

        return matched.stream().distinct().collect(Collectors.toList());
    }

    public record ProductSearchResult(long total, List<Product> products) {}
}
