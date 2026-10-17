package com.example.historyq.repository;

import com.example.historyq.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import com.example.historyq.entity.User;
import java.util.Optional;


public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByUploadedBy(User user);
    Optional<Document> findByIdAndUploadedBy(UUID id, User user);
    List<Document> findByVisibilityOrderByDisplayNameAsc(String visibility);
    List<Document> findByVisibilityAndDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(
            String visibility,
            String displayName
    );



}
