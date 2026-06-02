package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutfitSuggestionServiceTest {

    @Test
    void fallsBackToRuleBasedComboWhenGeminiFails() {
        OutfitCacheManager cache = mock(OutfitCacheManager.class);
        ProductRetrieverService products = mock(ProductRetrieverService.class);
        OutfitCandidateRetriever candidates = mock(OutfitCandidateRetriever.class);
        TagTranslationService tags = mock(TagTranslationService.class);
        AiClientRouter ai = mock(AiClientRouter.class);
        GeminiOutfitProvider gemini = mock(GeminiOutfitProvider.class);
        OutfitScoringService scoring = mock(OutfitScoringService.class);
        OutfitSuggestionService service = new OutfitSuggestionService(cache, products, candidates, tags, ai, gemini, scoring);

        ChatProductCard top = card(1L, "top", "MALE");
        ChatProductCard bottom = card(2L, "bottom", "MALE");
        when(products.findProductCard(1L, 11L)).thenReturn(Optional.of(top));
        when(candidates.getCandidatesForSlot(top, "bottom", 40)).thenReturn(List.of(bottom));
        when(gemini.generateCombos(any(), anyString(), any(), any(), any(), any())).thenReturn(List.of());
        when(tags.labelForStyle(anyString())).thenReturn("daily");
        when(ai.generate(anyString(), any(), anyString())).thenThrow(new IllegalStateException("Gemini down"));

        List<OutfitComboResponse> result = service.buildCombos(1L, 11L);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(combo -> "RULE".equals(combo.getProvider()));
        assertThat(result.get(0).getTopSlot().getProductId()).isEqualTo(1L);
        assertThat(result.get(0).getBottomSlot().getProductId()).isEqualTo(2L);
    }

    private ChatProductCard card(Long id, String role, String gender) {
        return ChatProductCard.builder()
            .id(id)
            .colorId(id + 10)
            .name(role)
            .role(role)
            .gender(gender)
            .colorFamily("neutral")
            .build();
    }
}
