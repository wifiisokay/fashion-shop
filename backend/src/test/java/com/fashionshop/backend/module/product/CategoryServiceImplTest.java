package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.ai.CategoryKeywordMapper;
import com.fashionshop.backend.common.enums.CategoryRole;
import com.fashionshop.backend.module.product.dto.request.CategoryRequest;
import com.fashionshop.backend.module.product.dto.response.CategoryResponse;
import com.fashionshop.backend.module.product.dto.response.CategoryTreeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryKeywordMapper categoryKeywordMapper;
    @InjectMocks private CategoryServiceImpl categoryService;

    private Category rootCategory;
    private Category childCategory;
    private Category rootCategory2;

    @BeforeEach
    void setUp() {
        rootCategory = Category.builder()
            .id(1).name("Áo").slug("ao").description("Danh mục áo")
            .children(new ArrayList<>())
            .build();

        childCategory = Category.builder()
            .id(3).name("Áo Nam").slug("ao-nam").description("Áo nam")
            .parent(rootCategory)
            .children(new ArrayList<>())
            .build();

        rootCategory.getChildren().add(childCategory);

        rootCategory2 = Category.builder()
            .id(2).name("Quần").slug("quan").description("Danh mục quần")
            .children(new ArrayList<>())
            .build();
    }

    // ================================================================
    // getTree
    // ================================================================

    @Test
    void getTree_returnsRootsWithChildren() {
        when(categoryRepository.findByParentIsNullOrderByNameAsc())
            .thenReturn(List.of(rootCategory, rootCategory2));

        List<CategoryTreeResponse> result = categoryService.getTree();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Áo");
        assertThat(result.get(0).getChildren()).hasSize(1);
        assertThat(result.get(0).getChildren().get(0).getName()).isEqualTo("Áo Nam");
        assertThat(result.get(1).getName()).isEqualTo("Quần");
        assertThat(result.get(1).getChildren()).isEmpty();
    }

    @Test
    void getTree_returnsEmptyList_whenNoCategories() {
        when(categoryRepository.findByParentIsNullOrderByNameAsc())
            .thenReturn(Collections.emptyList());

        List<CategoryTreeResponse> result = categoryService.getTree();

        assertThat(result).isEmpty();
    }

    // ================================================================
    // getById
    // ================================================================

    @Test
    void getById_returnsCategory_whenExists() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));

        CategoryResponse result = categoryService.getById(1);

        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getName()).isEqualTo("Áo");
        assertThat(result.getSlug()).isEqualTo("ao");
    }

    @Test
    void getById_throwsNotFound_whenNotExists() {
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(99))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    // ================================================================
    // create
    // ================================================================

    @Test
    void create_savesRootCategory_whenNoParentId() {
        CategoryRequest request = buildCategoryRequest("Giày", null);
        when(categoryRepository.existsBySlug("giay")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse result = categoryService.create(request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());

        Category saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Giày");
        assertThat(saved.getSlug()).isEqualTo("giay");
        assertThat(saved.getParent()).isNull();
    }

    @Test
    void create_savesChildCategory_whenValidParentId() {
        CategoryRequest request = buildCategoryRequest("Áo Polo", 1);
        when(categoryRepository.existsBySlug("ao-polo")).thenReturn(false);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.create(request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getParent()).isEqualTo(rootCategory);
    }

    @Test
    void create_generatesVietnameseSlug_correctly() {
        CategoryRequest request = buildCategoryRequest("Thời Trang Đường Phố", null);
        when(categoryRepository.existsBySlug("thoi-trang-duong-pho")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.create(request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("thoi-trang-duong-pho");
    }

    @Test
    void create_throwsConflict_whenSlugExists() {
        CategoryRequest request = buildCategoryRequest("Áo", null);
        when(categoryRepository.existsBySlug("ao")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_SLUG_EXISTS);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_throwsNotFound_whenParentNotExists() {
        CategoryRequest request = buildCategoryRequest("Test", 99);
        when(categoryRepository.existsBySlug(any())).thenReturn(false);
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void create_throwsBadRequest_whenParentIsNotRoot() {
        // childCategory đã có parent → không được làm cha
        CategoryRequest request = buildCategoryRequest("Cháu", 3);
        when(categoryRepository.existsBySlug(any())).thenReturn(false);
        when(categoryRepository.findById(3)).thenReturn(Optional.of(childCategory));

        assertThatThrownBy(() -> categoryService.create(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_INVALID_PARENT);
    }

    @Test
    void create_throwsBadRequest_whenChildRoleIsMissing() {
        CategoryRequest request = buildCategoryRequest("Ao Hoodie Nam", 1);
        request.setRole(null);
        when(categoryRepository.existsBySlug("ao-hoodie-nam")).thenReturn(false);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));

        assertThatThrownBy(() -> categoryService.create(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_ROLE_REQUIRED);
    }

    // ================================================================
    // update
    // ================================================================

    @Test
    void update_updatesFields_correctly() {
        CategoryRequest request = buildCategoryRequest("Áo Mới", null);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.findBySlug("ao-moi")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse result = categoryService.update(1, request);

        assertThat(rootCategory.getName()).isEqualTo("Áo Mới");
        assertThat(rootCategory.getSlug()).isEqualTo("ao-moi");
    }

    @Test
    void update_throwsConflict_whenSlugExistsOnOther() {
        CategoryRequest request = buildCategoryRequest("Quần", null);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.findBySlug("quan")).thenReturn(Optional.of(rootCategory2));

        assertThatThrownBy(() -> categoryService.update(1, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_SLUG_EXISTS);
    }

    @Test
    void update_allowsSameSlug_whenSameCategory() {
        CategoryRequest request = buildCategoryRequest("Áo", null);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.findBySlug("ao")).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Không throw — slug trùng chính nó
        CategoryResponse result = categoryService.update(1, request);
        assertThat(result).isNotNull();
    }

    @Test
    void update_throwsBadRequest_whenSelfReference() {
        CategoryRequest request = buildCategoryRequest("Áo", null);
        request.setParentId(1);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));
        when(categoryRepository.findBySlug("ao")).thenReturn(Optional.of(rootCategory));

        assertThatThrownBy(() -> categoryService.update(1, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_INVALID_PARENT);
    }

    @Test
    void update_setsParentNull_whenParentIdNull() {
        // child → update parentId=null → trở thành root
        CategoryRequest request = buildCategoryRequest("Áo Nam", null);
        when(categoryRepository.findById(3)).thenReturn(Optional.of(childCategory));
        when(categoryRepository.findBySlug("ao-nam")).thenReturn(Optional.of(childCategory));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        categoryService.update(3, request);

        assertThat(childCategory.getParent()).isNull();
    }

    @Test
    void update_throwsNotFound_whenCategoryNotExists() {
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99, buildCategoryRequest("X", null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    // ================================================================
    // delete
    // ================================================================

    @Test
    void delete_deletesCategory_whenNoProducts() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));
        when(productRepository.existsByCategoryId(1)).thenReturn(false);

        categoryService.delete(1);

        verify(categoryRepository).delete(rootCategory);
    }

    @Test
    void delete_throwsConflict_whenHasProducts() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rootCategory));
        when(productRepository.existsByCategoryId(1)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_HAS_ACTIVE_PRODUCTS);

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_throwsNotFound_whenCategoryNotExists() {
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(99))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_NOT_FOUND);
    }

    // ================================================================
    // Fixtures
    // ================================================================

    private CategoryRequest buildCategoryRequest(String name, Integer parentId) {
        CategoryRequest r = new CategoryRequest();
        r.setName(name);
        r.setDescription("Mô tả " + name);
        r.setParentId(parentId);
        if (parentId != null) {
            r.setRole(CategoryRole.TOP);
        }
        return r;
    }
}
