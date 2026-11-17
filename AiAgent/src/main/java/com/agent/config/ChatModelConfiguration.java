package com.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the chat model shared by the application services.
 */
@Configuration
public class ChatModelConfiguration {

    @Bean
    public OllamaChatRequestParameters ollamaRequestParameters(
            @Value("${ai.ollama.num-ctx:4096}") int contextWindow,
            @Value("${ai.output.max-tokens:256}") int maxOutputTokens,
            @Value("${ai.ollama.think:false}") boolean think) {
        if (maxOutputTokens <= 0 || maxOutputTokens >= contextWindow) {
            throw new IllegalArgumentException(
                    "ai.ollama.num-ctx must be positive and greater than ai.output.max-tokens");
        }

        return OllamaChatRequestParameters.builder()
                .numCtx(contextWindow)
                .maxOutputTokens(maxOutputTokens)
                .think(think)
                .truncate(false)
                .build();
    }

    /**
     * Creates the synchronous Ollama chat model used for explicit memory extraction.
     *
     * @param baseUrl    Ollama server URL configured by {@code ai.base-url}
     * @param modelName  Ollama model name configured by {@code ai.model}
     * @param parameters default Ollama request parameters
     * @return configured Ollama chat model
     */
    @Bean
    public ChatModel chatModel(
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.model}") String modelName,
            OllamaChatRequestParameters parameters) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .defaultRequestParameters(parameters)
                .build();
    }

    /**
     * Creates the streaming Ollama chat model used for user-facing replies.
     *
     * @param baseUrl    Ollama server URL configured by {@code ai.base-url}
     * @param modelName  Ollama model name configured by {@code ai.model}
     * @param parameters default Ollama request parameters
     * @return configured streaming Ollama chat model
     */
    @Bean
    public StreamingChatModel streamingChatModel(
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.model}") String modelName,
            OllamaChatRequestParameters parameters) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .defaultRequestParameters(parameters)
                .build();
    }
}
