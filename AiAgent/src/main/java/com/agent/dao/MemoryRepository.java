package com.agent.dao;

import com.agent.dto.Memory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemoryRepository
        extends JpaRepository<Memory, UUID> {

    List<Memory> findTop20ByOrderByImportanceDesc();

    boolean existsByMemoryText(String memoryText);
}