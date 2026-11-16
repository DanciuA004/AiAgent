package com.agent.dao;

import com.agent.dto.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository
        extends JpaRepository<Message, UUID> {

    List<Message> findTop20ByOrderByCreatedAtDesc();
}