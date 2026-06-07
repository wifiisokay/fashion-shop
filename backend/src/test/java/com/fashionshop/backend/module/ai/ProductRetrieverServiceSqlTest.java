package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.repository.CategoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductRetrieverServiceSqlTest {

    @Test
    void buildsProductFirstGroundedSqlWithStrictFilters() {
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        when(categoryRepository.findAll()).thenReturn(List.of());
        ProductRetrieverService service = new ProductRetrieverService(
            new CategoryKeywordMapper(categoryRepository),
            new TagTranslationService()
        );

        String sql = service.buildSearchSqlForAudit("áo sơ mi trắng nam đi làm dưới 500k");
        String countSql = service.buildCountSqlForAudit("áo sơ mi trắng nam đi làm dưới 500k");

        assertThat(sql)
            .contains("p.status = 'ACTIVE'")
            .contains("pv.stock_quantity > 0")
            .contains("p.gender = :gender")
            .contains("<= :maxPrice")
            .contains("LOWER(pc.color_name) LIKE :colorKeyword")
            .contains("JSON_CONTAINS(p.occasion_tags, JSON_QUOTE(:occasionTag))")
            .contains("LOWER(COALESCE(p.name, ''))")
            .contains("LOWER(COALESCE(p.description, ''))")
            .contains("LOWER(COALESCE(c.name, ''))")
            .contains("LOWER(COALESCE(c.slug, ''))")
            .contains("CAST(p.style_tags AS CHAR)")
            .contains("CAST(p.occasion_tags AS CHAR)")
            .contains("LOWER(COALESCE(p.fit_type, ''))")
            .contains("LOWER(COALESCE(p.season, ''))")
            .contains("LOWER(COALESCE(p.material, ''))")
            .contains("LOWER(COALESCE(pc.color_family, ''))");
        assertThat(countSql)
            .contains("LEFT JOIN categories c ON c.id = p.category_id")
            .contains("LOWER(COALESCE(c.name, ''))");
    }

    @Test
    void treatsDarkColorRequestAsDarkFilter() {
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        when(categoryRepository.findAll()).thenReturn(List.of());
        ProductRetrieverService service = new ProductRetrieverService(
            new CategoryKeywordMapper(categoryRepository),
            new TagTranslationService()
        );

        String sql = service.buildSearchSqlForAudit("goi y phoi do ao thoang mat mau toi");

        assertThat(sql)
            .contains("LOWER(pc.color_name) LIKE '%đen%'")
            .contains("LOWER(pc.color_name) LIKE '%navy%'")
            .contains("LOWER(pc.color_family) IN ('cool','neutral')");
    }
}
