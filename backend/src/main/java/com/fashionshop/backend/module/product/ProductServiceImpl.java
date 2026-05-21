package com.fashionshop.backend.module.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ReviewRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductRequest;
import com.fashionshop.backend.module.product.dto.request.ProductStatusRequest;
import com.fashionshop.backend.module.product.dto.response.ProductDetailResponse;
import com.fashionshop.backend.module.product.dto.response.ProductSummaryResponse;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;

    // ===================== ADMIN =====================

    @Override
    @Transactional
    public ProductDetailResponse create(ProductRequest request, User currentUser) {
        validateSalePrice(request);
        validateTags(request);
        validateSingleValueFields(request);
        Category category = findCategoryOrThrow(request.getCategoryId());

        Product product = Product.builder()
            .name(request.getName().trim())
            .description(request.getDescription())
            .basePrice(request.getBasePrice())
            .salePrice(request.getSalePrice())
            .isSale(request.getIsSale())
            .gender(request.getGender())
            .material(request.getMaterial())
            .estimatedWeight(request.getEstimatedWeight() != null ? request.getEstimatedWeight() : 300)
            .fitType(request.getFitType())
            .season(request.getSeason())
            .styleTags(request.getStyleTags() != null ? request.getStyleTags() : new ArrayList<>())
            .occasionTags(request.getOccasionTags() != null ? request.getOccasionTags() : new ArrayList<>())
            .category(category)
            .createdBy(currentUser)
            .build();

        return ProductDetailResponse.from(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductDetailResponse update(Long id, ProductRequest request) {
        Product product = findProductOrThrow(id);
        validateSalePrice(request);
        validateTags(request);
        validateSingleValueFields(request);
        Category category = findCategoryOrThrow(request.getCategoryId());

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setSalePrice(request.getSalePrice());
        product.setIsSale(request.getIsSale());
        product.setGender(request.getGender());
        product.setMaterial(request.getMaterial());
        product.setEstimatedWeight(request.getEstimatedWeight() != null ? request.getEstimatedWeight() : product.getEstimatedWeight());
        product.setFitType(request.getFitType());
        product.setSeason(request.getSeason());
        product.setStyleTags(request.getStyleTags() != null ? request.getStyleTags() : new ArrayList<>());
        product.setOccasionTags(request.getOccasionTags() != null ? request.getOccasionTags() : new ArrayList<>());
        product.setCategory(category);

        return ProductDetailResponse.from(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductDetailResponse updateStatus(Long id, ProductStatusRequest request) {
        Product product = findProductOrThrow(id);
        product.setStatus(request.getStatus());
        return ProductDetailResponse.from(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getByIdAdmin(Long id) {
        return ProductDetailResponse.from(findProductOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> listAdmin(String keyword, Integer categoryId, String status,
                                                           String gender, int page, int size) {
        Specification<Product> spec = buildSpec(keyword, categoryId, gender, null, null, null, null, null, false);

        // Admin filter thêm status
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), ProductStatus.valueOf(status)));
        }

        Page<Product> products = productRepository.findAll(spec,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return enrichWithReviewStats(products);
    }

    // ===================== PUBLIC =====================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> listPublic(String keyword, Integer categoryId, String gender,
                                                           Boolean isSale, String color, String sizeOption,
                                                           BigDecimal minPrice, BigDecimal maxPrice,
                                                           String sort, int page, int size) {
        Specification<Product> spec = buildSpec(keyword, categoryId, gender, isSale, color, sizeOption, minPrice, maxPrice, true);

        Sort sortOrder = resolveSort(sort);
        Page<Product> products = productRepository.findAll(spec, PageRequest.of(page, size, sortOrder));

        return enrichWithReviewStats(products);
    }

    /** Batch fill avgRating + reviewCount cho listing page. */
    private PageResponse<ProductSummaryResponse> enrichWithReviewStats(Page<Product> products) {
        List<ProductSummaryResponse> summaries = products.getContent().stream()
            .map(ProductSummaryResponse::from).collect(Collectors.toList());

        List<Long> productIds = summaries.stream().map(ProductSummaryResponse::getId).toList();
        if (!productIds.isEmpty()) {
            Map<Long, double[]> statsMap = reviewRepository.getBatchStatsByProductIds(productIds).stream()
                .collect(Collectors.toMap(
                    row -> ((Number) row[0]).longValue(),
                    row -> new double[]{
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).doubleValue()
                    }
                ));
            summaries.forEach(s -> {
                double[] stat = statsMap.get(s.getId());
                if (stat != null) {
                    s.setAvgRating(Math.round(stat[0] * 10.0) / 10.0);
                    s.setReviewCount((int) stat[1]);
                }
            });
        }

        return PageResponse.from(summaries, products);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getByIdPublic(Long id) {
        Product product = findProductOrThrow(id);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Sản phẩm không tồn tại");
        }
        return ProductDetailResponse.from(product);
    }

    // ===================== PRIVATE =====================

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private Category findCategoryOrThrow(Integer id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private void validateSalePrice(ProductRequest req) {
        if (Boolean.TRUE.equals(req.getIsSale())) {
            if (req.getSalePrice() == null || req.getSalePrice().compareTo(req.getBasePrice()) >= 0) {
                throw new BusinessException(ErrorCode.INVALID_SALE_PRICE, HttpStatus.BAD_REQUEST,
                    "Giá khuyến mãi phải nhỏ hơn giá gốc");
            }
        }
    }

    /** Validate style_tags và occasion_tags phải nằm trong ProductTagLibrary. */
    private void validateTags(ProductRequest req) {
        if (req.getStyleTags() != null && !req.getStyleTags().isEmpty()) {
            List<String> invalidStyle = req.getStyleTags().stream()
                .filter(t -> !ProductTagLibrary.STYLE_TAGS.contains(t))
                .toList();
            if (!invalidStyle.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_TAG, HttpStatus.BAD_REQUEST,
                    "style_tags không hợp lệ: " + invalidStyle + ". Xem danh sách tại GET /api/admin/products/tag-library");
            }
        }
        if (req.getOccasionTags() != null && !req.getOccasionTags().isEmpty()) {
            List<String> invalidOccasion = req.getOccasionTags().stream()
                .filter(t -> !ProductTagLibrary.OCCASION_TAGS.contains(t))
                .toList();
            if (!invalidOccasion.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_TAG, HttpStatus.BAD_REQUEST,
                    "occasion_tags không hợp lệ: " + invalidOccasion + ". Xem danh sách tại GET /api/admin/products/tag-library");
            }
        }
    }

    /** Validate các single-value field phải nằm trong tập hợp chuẩn hóa. */
    private void validateSingleValueFields(ProductRequest req) {
        if (req.getFitType() != null && !req.getFitType().isBlank()
                && !ProductTagLibrary.FIT_TYPES.contains(req.getFitType())) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, HttpStatus.BAD_REQUEST,
                "fit_type '" + req.getFitType() + "' không hợp lệ. Giá trị chấp nhận: " + ProductTagLibrary.FIT_TYPES);
        }
        if (req.getSeason() != null && !req.getSeason().isBlank()
                && !ProductTagLibrary.SEASONS.contains(req.getSeason())) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, HttpStatus.BAD_REQUEST,
                "season '" + req.getSeason() + "' không hợp lệ. Giá trị chấp nhận: " + ProductTagLibrary.SEASONS);
        }
    }

    /**
     * Build JPA Specification cho filter params.
     * publicOnly = true → chỉ ACTIVE + có variant còn hàng.
     */
    private Specification<Product> buildSpec(String keyword, Integer categoryId, String gender,
                                             Boolean isSale, String color, String sizeOption,
                                             BigDecimal minPrice, BigDecimal maxPrice, boolean publicOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (publicOnly) {
                predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            }

            // Filter theo color → join qua product_colors
            if (color != null && !color.isBlank()) {
                query.distinct(true);
                Join<Product, ProductColor> colorJoin = root.join("colors", JoinType.INNER);
                predicates.add(cb.equal(colorJoin.get("colorName"), color));
            }

            // Filter theo size / publicOnly → join qua variants
            if (publicOnly || (sizeOption != null && !sizeOption.isBlank())) {
                query.distinct(true);
                Join<Product, ProductVariant> variantJoin = root.join("variants", JoinType.INNER);
                
                if (publicOnly) {
                    predicates.add(cb.greaterThan(variantJoin.get("stockQuantity"), 0));
                }
                if (sizeOption != null && !sizeOption.isBlank()) {
                    predicates.add(cb.equal(variantJoin.get("size"), sizeOption));
                }
            }

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
                ));
            }

            if (categoryId != null) {
                // Lọc theo category + tất cả con của nó
                List<Integer> categoryIds = getCategoryIdsIncludingChildren(categoryId);
                predicates.add(root.get("category").get("id").in(categoryIds));
            }

            if (gender != null && !gender.isBlank()) {
                predicates.add(cb.equal(root.get("gender"), Gender.valueOf(gender)));
            }

            if (isSale != null) {
                predicates.add(cb.equal(root.get("isSale"), isSale));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), maxPrice));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Lấy ID category + tất cả con (cách đơn giản cho cây 2 cấp). */
    private List<Integer> getCategoryIdsIncludingChildren(Integer categoryId) {
        List<Integer> ids = new ArrayList<>();
        ids.add(categoryId);
        List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(categoryId);
        children.forEach(child -> ids.add(child.getId()));
        return ids;
    }

    private Sort resolveSort(String sort) {
        if (sort == null) return Sort.by(Sort.Direction.DESC, "createdAt");
        return switch (sort) {
            case "price_asc"  -> Sort.by(Sort.Direction.ASC, "basePrice");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "basePrice");
            case "name_asc"   -> Sort.by(Sort.Direction.ASC, "name");
            default           -> Sort.by(Sort.Direction.DESC, "createdAt"); // "newest"
        };
    }
}
