package com.agent.service;

import com.agent.dao.MemoryRepository;
import com.agent.dto.Memory;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Determines which user details to remember and manages stored memories.
 */
@Service
public class MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryRepository memoryRepository;
    private final ChatModel model;

    /**
     * Creates the memory service.
     *
     * @param memoryRepository repository for long-term memories
     * @param model            configured chat model used to evaluate and extract memories
     */
    public MemoryService(MemoryRepository memoryRepository, ChatModel model) {
        this.memoryRepository = memoryRepository;
        this.model = model;
    }

    /**
     * Extracts and saves a memory when the model considers the user message worth retaining.
     *
     * @param userMessage message to evaluate
     */
    public void rememberIfRelevant(String userMessage) {
        if (shouldRemember(userMessage)) {
            saveMemory(extractMemory(userMessage));
        }
    }

    /**
     * Retrieves and joins saved memory text for use as model context.
     *
     * @return formatted memory context
     */
    public String getMemoryContext() {
        return memoryRepository.findAll()
                .stream()
                .map(Memory::getMemoryText)
                .collect(Collectors.joining("\n"));
    }

    private boolean shouldRemember(String userMessage) {
        String prompt = """
                Determine whether the following
                information about the user should
                be remembered long-term.

                Only answer YES or NO.

                Message:

                %s
                """
                .formatted(userMessage);

        String result = model.chat(prompt);
        return result.toUpperCase().contains("YES");
    }

    private String extractMemory(String userMessage) {
        String prompt = """
                Rewrite the user's message as one concise, factual memory
                about the user. Return only the memory text, with no labels,
                explanation, or markdown. Never return an empty response.

                Example:

                Input:
                My favourite game is Minecraft.

                Output:
                User's favourite game is Minecraft.

                Message:
                %s
                """
                .formatted(userMessage);

        String memoryText = model.chat(prompt);
        if (memoryText == null || memoryText.isBlank()) {
            logger.warn("Memory extraction returned no text; storing the original user message instead.");
            return userMessage.strip();
        }

        return memoryText.strip();
    }

    private void saveMemory(String memoryText) {
        if (memoryText == null || memoryText.isBlank()) {
            logger.warn("Skipping memory persistence because the memory text is empty.");
            return;
        }

        Memory memory = new Memory();
        memory.setId(UUID.randomUUID());
        memory.setMemoryText(memoryText);
        memory.setImportance(100);
        memory.setCreatedAt(LocalDateTime.now());

        memoryRepository.save(memory);
    }
}
