package com.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the chat model shared by the application services.
 */
@Configuration
public class ChatModelConfiguration {

    /**
     * Creates the Ollama chat model from application properties.
     *
     * @param baseUrl   Ollama server URL configured by {@code ai.base-url}
     * @param modelName Ollama model name configured by {@code ai.model}
     * @return configured Ollama chat model
     */
    @Bean
    public ChatModel chatModel(
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.model}") String modelName) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }
}
