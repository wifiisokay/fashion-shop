package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryKeywordMapperTest {

    @Test
    void detectsSpecificDynamicCategoryBeforeBroadParent() {
        CategoryRepository repository = mock(CategoryRepository.class);
        when(repository.findAll()).thenReturn(seedCategories());
        CategoryKeywordMapper mapper = new CategoryKeywordMapper(repository);

        assertThat(mapper.detectCategoryIds("tìm áo sơ mi nam")).containsExactly(5);
        assertThat(mapper.detectCategoryIds("áo phông")).containsExactly(3, 9);
        assertThat(mapper.detectCategoryIds("1+1=?")).isEmpty();
    }

    @Test
    void resolvesNewCategoryFromNameWithoutAdminKeywords() {
        CategoryRepository repository = mock(CategoryRepository.class);
        Category hoodie = category(14, "Áo hoodie nam", "ao-hoodie-nam", null);
        when(repository.findAll()).thenReturn(List.of(hoodie));
        CategoryKeywordMapper mapper = new CategoryKeywordMapper(repository);

        assertThat(mapper.detectCategoryIds("hoodie nam mặc với gì")).containsExactly(14);
        assertThat(mapper.detectCategoryIds("áo nỉ nam dưới 500k")).containsExactly(14);
    }

    private List<Category> seedCategories() {
        Category male = category(1, "Thời trang nam", "thoi-trang-nam", null);
        Category female = category(2, "Thời trang nữ", "thoi-trang-nu", null);
        return List.of(
            male,
            female,
            category(3, "Áo thun nam", "ao-thun-nam", male),
            category(5, "Áo sơ mi nam", "ao-somi-nam", male),
            category(9, "Áo thun nữ", "ao-thun-nu", female)
        );
    }

    private Category category(Integer id, String name, String slug, Category parent) {
        return Category.builder()
            .id(id)
            .name(name)
            .slug(slug)
            .parent(parent)
            .build();
    }
}
