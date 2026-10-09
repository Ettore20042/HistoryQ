package com.example.historyq.repository;

import com.example.historyq.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import com.example.historyq.entity.User;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByUploadedBy(User user);



}
