package com.fashionshop.backend.module.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StyleAnswerResult {
    private String content;
    private List<String> styleTips;
    private List<String> suggestedQuestions;
}
