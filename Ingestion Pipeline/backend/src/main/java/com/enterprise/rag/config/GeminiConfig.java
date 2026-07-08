package com.enterprise.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${google.gemini.api-key}")
    private String geminiApiKey;

    @Value("${google.gemini.ai-model}")
    private String aiModel;

    @Value("${google.gemini.ai-dim}")
    private int aiDim;

    @Bean
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(geminiApiKey)
                .modelName(aiModel)
                .outputDimensionality(aiDim)
                .build();
    }
}