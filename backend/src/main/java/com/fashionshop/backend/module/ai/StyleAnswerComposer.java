package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.ChatContext;
import com.fashionshop.backend.module.ai.dto.StyleAnswerResult;
import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StyleAnswerComposer {

    private final GeminiStyleAnswerService geminiStyleAnswerService;
    private final RuleBasedStyleAnswerComposer ruleBasedStyleAnswerComposer;

    public StyleAnswerResult compose(ChatContext context, ChatProductCard anchor, List<OutfitComboResponse> combos) {
        GeminiStyleAnswerService.StyleAnswerPayload payload = geminiStyleAnswerService.generate(context, anchor, combos);
        if (payload != null) {
            if (payload.comboExplanations != null && combos != null) {
                for (int i = 0; i < combos.size(); i++) {
                    String targetComboId = "combo-" + (i + 1);
                    for (GeminiStyleAnswerService.ComboExplanation expl : payload.comboExplanations) {
                        if (targetComboId.equalsIgnoreCase(expl.comboId)) {
                            combos.get(i).setDescription(expl.reason);
                            combos.get(i).setReason(expl.reason);
                        }
                    }
                }
            }
            return StyleAnswerResult.builder()
                .content(payload.content)
                .styleTips(payload.styleTips)
                .suggestedQuestions(payload.suggestedQuestions)
                .build();
        }
        log.info("[AI_STYLE_ANSWER] fallback to rule-based");
        return ruleBasedStyleAnswerComposer.compose(context, anchor, combos);
    }
}
