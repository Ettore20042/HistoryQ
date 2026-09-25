package com.example.historyq.repository;

import com.example.historyq.entity.CollectionDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CollectionDocumentRepository extends JpaRepository<CollectionDocument, UUID> {
}
