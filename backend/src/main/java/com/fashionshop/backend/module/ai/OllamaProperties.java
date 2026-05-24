package com.fashionshop.backend.module.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
    private String baseUrl = "http://localhost:11434";
    private String model = "gemma4:e4b";
    private int timeoutSeconds = 30;
}
