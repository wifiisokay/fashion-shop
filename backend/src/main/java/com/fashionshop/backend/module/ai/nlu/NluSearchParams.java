package com.fashionshop.backend.module.ai.nlu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NluSearchParams {
    private String intent;
    private String gender;
    private String colorFamily;
    private String colorKeyword;
    private List<String> categoryKeywords;
    private List<String> occasionKeywords;
    private List<String> styleKeywords;
    private String fitKeyword;
    private Long priceMax;
    @JsonProperty("isSale")
    private Boolean isSale;
    @JsonProperty("isFollowUp")
    private boolean isFollowUp;
}
