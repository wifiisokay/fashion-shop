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
