package com.agent.service;

import com.agent.dao.MemoryRepository;
import com.agent.dto.Memory;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Determines which user details to remember and manages stored memories.
 */
@Service
public class MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);
    private static final Pattern PERSONAL_FACT_CUE = Pattern.compile(
            "\\b(?:i(?:'m| am| live| work| like| love| prefer| have| own| speak| enjoy| dislike|"
                    + " play| study| use| was born| grew up)|"
                    + "my (?:favorite|favourite|name|job|work|family|hobby|goal|birthday)\\b)",
            Pattern.CASE_INSENSITIVE);

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
        if (userMessage == null || !PERSONAL_FACT_CUE.matcher(userMessage).find()) {
            logger.debug("Skipped memory evaluation because the message has no personal-fact cue.");
            return;
        }

        long start = System.nanoTime();
        String memoryText = extractMemoryOrNone(userMessage);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        logger.debug("Memory evaluation completed in {} ms", elapsedMillis);

        if (memoryText != null && !memoryText.equalsIgnoreCase("NONE")) {
            saveMemory(memoryText);
        }
    }

    /**
     * Retrieves and joins saved memory text for use as model context.
     *
     * @return formatted memory context
     */
    public String getMemoryContext() {
        long start = System.nanoTime();
        String context = memoryRepository.findTop20ByOrderByImportanceDesc()
                .stream()
                .map(Memory::getMemoryText)
                .collect(Collectors.joining("\n"));
        logger.debug("Loaded memory context in {} ms", (System.nanoTime() - start) / 1_000_000);
        return context;
    }

    private String extractMemoryOrNone(String userMessage) {
        String prompt = """
                Decide whether the message contains a durable, useful fact about the user.
                If it does, rewrite that fact as one concise, factual memory.
                If it does not, return exactly NONE.
                Return only NONE or the memory text, with no labels, explanation, or markdown.
                
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
            logger.warn("Memory evaluation returned no text; skipping memory persistence.");
            return null;
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
