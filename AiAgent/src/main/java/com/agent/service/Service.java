package com.agent.service;

import com.agent.dao.MemoryRepository;
import com.agent.dao.MessageRepository;
import com.agent.dto.Memory;
import com.agent.dto.Message;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles chat requests by maintaining conversation context and querying the configured language model.
 */
@org.springframework.stereotype.Service
public class Service {

    private final MessageRepository messageRepository;
    private final MemoryRepository memoryRepository;
    private final ChatModel model;

    /**
     * Creates the chat service and configures its Ollama model from application properties.
     *
     * @param baseUrl   Ollama server URL configured by {@code ai.base-url}
     * @param modelName Ollama model name configured by {@code ai.model}
     */
    public Service(
            MessageRepository messageRepository,
            MemoryRepository memoryRepository,
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.model}") String modelName) {

        this.messageRepository = messageRepository;
        this.memoryRepository = memoryRepository;
        this.model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    /**
     * Adds the user message to the conversation, asks the model for a response, and saves it to history.
     *
     * @param message latest user message
     * @return the model's response
     */
    public String chat(String message) {

        saveMessage("user", message);

        String history = buildConversationHistory();
        String memories = buildMemoryContext();

        String prompt = """
                You are Nova, an AI companion.
                
                Things you know about the user:
                
                %s
                
                Recent conversation:
                
                %s
                
                Current user message:
                
                %s
                """
                .formatted(memories, history, message);

        String response = model.chat(prompt);

        saveMessage("assistant", response);

        return response;
    }

    private void saveMessage(
            String role,
            String content) {

        Message message = new Message();

        message.setId(UUID.randomUUID());
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());

        messageRepository.save(message);
    }

    private String buildConversationHistory() {

        List<Message> messages =
                messageRepository.findTop20ByOrderByCreatedAtDesc();

        Collections.reverse(messages);

        return messages.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String buildMemoryContext() {

        return memoryRepository.findAll()
                .stream()
                .map(Memory::getMemoryText)
                .collect(Collectors.joining("\n"));
    }

}