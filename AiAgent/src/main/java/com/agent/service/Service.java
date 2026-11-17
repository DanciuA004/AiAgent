package com.agent.service;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates message history, long-term memory, and responses from the chat model.
 */
@org.springframework.stereotype.Service
public class Service {

    private static final Logger logger = LoggerFactory.getLogger(Service.class);
    private static final int APPROX_CHARS_PER_TOKEN = 4;
    private static final String PROMPT_TEMPLATE = """
            You are Nova, a concise conversational and gaming assistant.
            Keep replies brief and actionable. Do not include internal reasoning.
            
            Memories:
            %s
            
            History:
            %s
            
            User:
            %s
            """;

    private final MessageService messageService;
    private final MemoryService memoryService;
    private final StreamingChatModel model;
    private final int inputContextTokens;
    private final int contextWindowTokens;
    private final int maxOutputTokens;
    private final int contextSafetyMarginTokens;
    private final long streamTimeoutMillis;

    /**
     * Creates the chat service.
     *
     * @param messageService service for message persistence and conversation history
     * @param memoryService  service for long-term memory
     * @param model          configured chat model
     */
    public Service(
            MessageService messageService,
            MemoryService memoryService,
            StreamingChatModel model,
            @Value("${ai.context.max-tokens:3072}") int inputContextTokens,
            @Value("${ai.ollama.num-ctx:4096}") int contextWindowTokens,
            @Value("${ai.output.max-tokens:256}") int maxOutputTokens,
            @Value("${ai.context.safety-margin-tokens:256}") int contextSafetyMarginTokens,
            @Value("${ai.http.stream-timeout-ms:120000}") long streamTimeoutMillis) {
        this.messageService = messageService;
        this.memoryService = memoryService;
        this.model = model;
        if (inputContextTokens <= 0 || contextSafetyMarginTokens < 0 || streamTimeoutMillis <= 0
                || maxOutputTokens <= 0 || maxOutputTokens >= contextWindowTokens
                || inputContextTokens > contextWindowTokens - maxOutputTokens - contextSafetyMarginTokens) {
            throw new IllegalArgumentException(
                    "Input context, output limit, and safety margin must fit within ai.ollama.num-ctx");
        }
        this.inputContextTokens = inputContextTokens;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.contextSafetyMarginTokens = contextSafetyMarginTokens;
        this.streamTimeoutMillis = streamTimeoutMillis;
    }

    /**
     * Adds the user message to the conversation, streams the model response, and saves it to history.
     *
     * @param userMessage latest user message
     * @return a server-sent event stream containing response text chunks
     */
    public SseEmitter streamChat(String userMessage) {
        long requestStart = System.nanoTime();

        long historyStart = System.nanoTime();
        String history = messageService.getRecentConversationHistory();
        long historyMillis = (System.nanoTime() - historyStart) / 1_000_000;
        messageService.saveMessage("user", userMessage);
        memoryService.rememberIfRelevant(userMessage);
        long memoryContextStart = System.nanoTime();
        String memories = memoryService.getMemoryContext();
        long memoryContextMillis = (System.nanoTime() - memoryContextStart) / 1_000_000;

        int effectiveInputTokens = Math.min(inputContextTokens,
                contextWindowTokens - maxOutputTokens - contextSafetyMarginTokens);
        int maxPromptChars = effectiveInputTokens * APPROX_CHARS_PER_TOKEN;
        int promptOverheadChars = PROMPT_TEMPLATE.formatted("", "", "").length();
        int availableContextChars = Math.max(0, maxPromptChars - promptOverheadChars);
        int historyCharLimit = availableContextChars * 35 / 100;
        int memoryCharLimit = availableContextChars * 15 / 100;
        int userMessageCharLimit = availableContextChars - historyCharLimit - memoryCharLimit;

        String prompt = PROMPT_TEMPLATE.formatted(
                truncate(memories, memoryCharLimit, false),
                truncate(history, historyCharLimit, true),
                truncate(userMessage, userMessageCharLimit, false));

        long modelStart = System.nanoTime();
        SseEmitter emitter = new SseEmitter(streamTimeoutMillis);
        AtomicBoolean completed = new AtomicBoolean();

        model.chat(prompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                if (completed.get()) {
                    context.streamingHandle().cancel();
                    return;
                }
                try {
                    emitter.send(SseEmitter.event().data(partialResponse.text()));
                } catch (IOException e) {
                    if (completed.compareAndSet(false, true)) {
                        emitter.completeWithError(e);
                    }
                    context.streamingHandle().cancel();
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                try {
                    String responseText = response.aiMessage().text();
                    messageService.saveMessage("assistant", responseText == null ? "" : responseText);
                    emitter.complete();
                } catch (RuntimeException e) {
                    logger.error("Failed to persist completed chat response", e);
                    emitter.completeWithError(e);
                    return;
                }
                logger.debug("Chat timings: historyDb={} ms, memoryContextDb={} ms, responseModel={} ms, total={} ms, promptChars={}",
                        historyMillis,
                        memoryContextMillis,
                        (System.nanoTime() - modelStart) / 1_000_000,
                        (System.nanoTime() - requestStart) / 1_000_000,
                        prompt.length());
            }

            @Override
            public void onError(Throwable error) {
                if (completed.compareAndSet(false, true)) {
                    logger.error("Chat model streaming failed", error);
                    emitter.completeWithError(error);
                }
            }
        });

        return emitter;
    }

    private String truncate(String text, int maxChars, boolean keepEnd) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }

        String marker = "...[truncated]";
        if (maxChars <= marker.length()) {
            return marker.substring(0, maxChars);
        }

        int retainedChars = maxChars - marker.length();
        if (keepEnd) {
            int start = text.length() - retainedChars;
            if (Character.isLowSurrogate(text.charAt(start))) {
                start++;
            }
            return marker + text.substring(start);
        }

        int end = retainedChars;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + marker;
    }
}