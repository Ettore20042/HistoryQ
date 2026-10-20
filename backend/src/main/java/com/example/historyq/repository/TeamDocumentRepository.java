package com.example.historyq.repository;

import com.example.historyq.entity.TeamDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface TeamDocumentRepository extends JpaRepository<TeamDocument, UUID> {
    List<TeamDocument> findByTeamId(UUID teamId);
    boolean existsByTeamIdAndDocumentId(UUID teamId, UUID documentId);
    Optional <TeamDocument> findByTeamIdAndDocumentId(UUID teamId, UUID documentId);
}