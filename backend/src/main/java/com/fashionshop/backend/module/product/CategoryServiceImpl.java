package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.CategoryRequest;
import com.fashionshop.backend.module.product.dto.response.CategoryResponse;
import com.fashionshop.backend.module.product.dto.response.CategoryTreeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getTree() {
        List<Category> roots = categoryRepository.findByParentIsNullOrderByNameAsc();
        return roots.stream().map(CategoryTreeResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Integer id) {
        Category category = findCategoryOrThrow(id);
        return CategoryResponse.from(category);
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String slug = generateSlug(request.getName());
        if (categoryRepository.existsBySlug(slug)) {
            throw new BusinessException(ErrorCode.CATEGORY_SLUG_EXISTS, HttpStatus.CONFLICT,
                "Slug '" + slug + "' đã tồn tại");
        }

        Category category = Category.builder()
            .name(request.getName().trim())
            .slug(slug)
            .description(request.getDescription())
            .build();

        if (request.getParentId() != null) {
            Category parent = findCategoryOrThrow(request.getParentId());
            validateParentIsRoot(parent);
            category.setParent(parent);
        }

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(Integer id, CategoryRequest request) {
        Category category = findCategoryOrThrow(id);

        String newSlug = generateSlug(request.getName());
        // Check slug unique — trừ chính nó
        categoryRepository.findBySlug(newSlug).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessException(ErrorCode.CATEGORY_SLUG_EXISTS, HttpStatus.CONFLICT,
                    "Slug '" + newSlug + "' đã tồn tại");
            }
        });

        category.setName(request.getName().trim());
        category.setSlug(newSlug);
        category.setDescription(request.getDescription());

        if (request.getParentId() != null) {
            // Không cho tự tham chiếu
            if (request.getParentId().equals(id)) {
                throw new BusinessException(ErrorCode.CATEGORY_INVALID_PARENT, HttpStatus.BAD_REQUEST,
                    "Danh mục không thể là cha của chính nó");
            }
            Category parent = findCategoryOrThrow(request.getParentId());
            validateParentIsRoot(parent);
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Category category = findCategoryOrThrow(id);

        // Chặn xóa nếu đang được dùng bởi product
        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_ACTIVE_PRODUCTS, HttpStatus.CONFLICT,
                "Danh mục đang có sản phẩm, không thể xóa");
        }

        categoryRepository.delete(category);
        // DB sẽ ON DELETE SET NULL cho các con
    }

    // ============ Private helpers ============

    private Category findCategoryOrThrow(Integer id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** Parent phải là root (không có parent) → enforce 2 cấp. */
    private void validateParentIsRoot(Category parent) {
        if (parent.getParent() != null) {
            throw new BusinessException(ErrorCode.CATEGORY_INVALID_PARENT, HttpStatus.BAD_REQUEST,
                "Chỉ hỗ trợ cây 2 cấp — danh mục cha phải là danh mục gốc");
        }
    }

    /** Sinh slug: lowercase, thay khoảng trắng/ký tự đặc biệt bằng '-'. */
    private String generateSlug(String name) {
        return name.trim().toLowerCase()
            .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
            .replaceAll("[èéẹẻẽêềếệểễ]", "e")
            .replaceAll("[ìíịỉĩ]", "i")
            .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
            .replaceAll("[ùúụủũưừứựửữ]", "u")
            .replaceAll("[ỳýỵỷỹ]", "y")
            .replaceAll("[đ]", "d")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }
}
