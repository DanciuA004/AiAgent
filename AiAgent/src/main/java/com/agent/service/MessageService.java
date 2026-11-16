package com.agent.service;

import com.agent.dao.MessageRepository;
import com.agent.dto.Message;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists messages and provides recent conversation history.
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;

    /**
     * Creates the message service.
     *
     * @param messageRepository repository for conversation messages
     */
    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Persists a message with its role, content, and creation time.
     *
     * @param role    message role, such as {@code user} or {@code assistant}
     * @param content message text
     */
    public void saveMessage(String role, String content) {
        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());

        messageRepository.save(message);
    }

    /**
     * Retrieves and formats the twenty most recent messages in chronological order.
     *
     * @return formatted conversation history
     */
    public String getRecentConversationHistory() {
        List<Message> messages = messageRepository.findTop20ByOrderByCreatedAtDesc();
        Collections.reverse(messages);

        return messages.stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .collect(Collectors.joining("\n"));
    }
}
