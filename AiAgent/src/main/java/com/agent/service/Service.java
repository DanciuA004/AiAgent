package com.agent.service;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Coordinates message history, long-term memory, and responses from the chat model.
 */
@org.springframework.stereotype.Service
public class Service {

    private final MessageService messageService;
    private final MemoryService memoryService;
    private final ChatModel model;

    /**
     * Creates the chat service.
     *
     * @param messageService service for message persistence and conversation history
     * @param memoryService  service for long-term memory
     * @param model           configured chat model
     */
    public Service(
            MessageService messageService,
            MemoryService memoryService,
            ChatModel model) {
        this.messageService = messageService;
        this.memoryService = memoryService;
        this.model = model;
    }

    /**
     * Adds the user message to the conversation, asks the model for a response, and saves it to history.
     *
     * @param userMessage latest user message
     * @return the model's response
     */
    public String chat(String userMessage) {

        messageService.saveMessage("user", userMessage);
        memoryService.rememberIfRelevant(userMessage);

        String history = messageService.getRecentConversationHistory();
        String memories = memoryService.getMemoryContext();

        String prompt = """
                You are Nova.
                
                Memories:
                %s
                
                History:
                %s
                
                User:
                %s
                """
                .formatted(memories,
                        history,
                        userMessage);

        String response = model.chat(prompt);

        messageService.saveMessage("assistant", response);

        return response;
    }
}