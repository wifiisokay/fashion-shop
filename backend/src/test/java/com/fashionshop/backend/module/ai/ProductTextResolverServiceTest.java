package com.fashionshop.backend.module.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class ProductTextResolverServiceTest {

    private ProductTextResolverService service;
    private Query query;

    @BeforeEach
    void setUp() {
        service = new ProductTextResolverService();
        EntityManager entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), anyString())).thenReturn(query);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void resolvesFemaleShirtCopiedFromProductName() {
        when(query.getResultList()).thenReturn(List.of((Object) row(70L,
            "Áo Sơ Mi Nữ Crop Tay Dài Cotton Kẻ Sọc Form Rộng", "FEMALE", 12L)));

        var match = service.resolveProductFromMessage(
            "Áo Sơ Mi Nữ Crop Tay Dài Cotton Kẻ Sọc Form Rộng gợi ý phối đồ gì?", "FEMALE");

        assertThat(match).isPresent();
        assertThat(match.get().productId()).isEqualTo(70L);
        assertThat(match.get().gender()).isEqualTo("FEMALE");
    }

    @Test
    void resolvesMalePoloAndFemaleDenim() {
        when(query.getResultList()).thenReturn(List.of(
            row(13L, "Áo Polo Nam Sợi Café Phối Gân Cổ Fitted", "MALE", 8L),
            row(83L, "Quần Denim Nữ Ống Rộng Dark Navy Form Straight", "FEMALE", 10L)
        ));

        assertThat(service.resolveProductFromMessage(
            "Áo Polo Nam Sợi Café Phối Gân Cổ Fitted mặc với gì?", "MALE"))
            .get().extracting(ProductTextResolverService.ProductMatch::productId).isEqualTo(13L);
        assertThat(service.resolveProductFromMessage(
            "Quần Denim Nữ Ống Rộng Dark Navy Form Straight phối với áo gì?", "FEMALE"))
            .get().extracting(ProductTextResolverService.ProductMatch::productId).isEqualTo(83L);
    }

    @Test
    void resolvesNormalizedTypoFreeTextAndRejectsUnknownHoodie() {
        when(query.getResultList()).thenReturn(List.of((Object) row(70L,
            "Áo Sơ Mi Nữ Crop Tay Dài Cotton Kẻ Sọc Form Rộng", "FEMALE", 12L)));

        assertThat(service.resolveProductFromMessage(
            "ao so mi nu crop tay dai cotton ke soc form rong phoi voi gi", "FEMALE"))
            .get().extracting(ProductTextResolverService.ProductMatch::productId).isEqualTo(70L);
        assertThat(service.resolveProductFromMessage("Áo hoodie nữ lông cừu phối gì?", "FEMALE")).isEmpty();
    }

    @Test
    void detectsGenderWithoutDefaultingToMale() {
        assertThat(service.detectGenderHint("Áo sơ mi nữ phối gì")).isEqualTo("FEMALE");
        assertThat(service.detectGenderHint("Áo polo nam phối gì")).isEqualTo("MALE");
        assertThat(service.detectGenderHint("Áo polo phối gì")).isNull();
    }

    @Test
    void rejectsAmbiguousCandidatesInsteadOfGuessing() {
        when(query.getResultList()).thenReturn(List.of(
            row(91L, "Áo Hoodie Nữ Lông Cừu Form Rộng Basic", "FEMALE", 4L),
            row(92L, "Áo Hoodie Nữ Lông Cừu Form Rộng Street", "FEMALE", 9L)
        ));

        assertThat(service.resolveProductFromMessage("Áo hoodie nữ lông cừu form rộng phối gì?", "FEMALE"))
            .isEmpty();
    }

    private Object[] row(Long id, String name, String gender, Long stock) {
        return new Object[] { id, name, "", gender, "", "", stock };
    }
}
