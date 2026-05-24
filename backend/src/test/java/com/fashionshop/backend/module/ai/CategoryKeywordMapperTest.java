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
    void detectsSpecificCategoryBeforeBroadCategory() {
        CategoryRepository repository = mock(CategoryRepository.class);
        when(repository.findAll()).thenReturn(seedCategories());
        CategoryKeywordMapper mapper = new CategoryKeywordMapper(repository);

        assertThat(mapper.detectCategoryIds("tìm áo sơ mi nam")).containsExactly(5);
        assertThat(mapper.detectCategoryIds("shop có áo sơ mi nào")).containsExactly(5, 10);
        assertThat(mapper.detectCategoryIds("áo thun")).containsExactly(3, 9);
        assertThat(mapper.detectCategoryIds("tìm áo nam")).containsExactly(3, 4, 5, 6, 9, 10, 11);
        assertThat(mapper.detectCategoryIds("1+1=?")).isEmpty();
    }

    private List<Category> seedCategories() {
        Category male = category(1, "Thời trang nam", "thoi-trang-nam", null);
        Category female = category(2, "Thời trang nữ", "thoi-trang-nu", null);
        return List.of(
            male,
            female,
            category(3, "Áo thun nam", "ao-thun-nam", male),
            category(4, "Áo polo nam", "ao-polo-nam", male),
            category(5, "Áo sơ mi nam", "ao-somi-nam", male),
            category(6, "Áo khoác nam", "ao-khoac-nam", male),
            category(7, "Quần dài nam", "quan-dai-nam", male),
            category(8, "Quần ngắn nam", "quan-ngan-nam", male),
            category(9, "Áo thun nữ", "ao-thun-nu", female),
            category(10, "Áo sơ mi nữ", "ao-somi-nu", female),
            category(11, "Áo khoác nữ", "ao-khoac-nu", female),
            category(12, "Quần dài nữ", "quan-dai-nu", female),
            category(13, "Váy & Đầm", "vay-va-dam", female)
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
