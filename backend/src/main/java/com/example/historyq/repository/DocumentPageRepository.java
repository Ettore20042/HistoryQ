
package com.example.historyq.repository;

import com.example.historyq.entity.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentPageRepository
        extends JpaRepository<DocumentPage, UUID> {

    List<DocumentPage> findAllByDocument_IdOrderByFilenameAsc(UUID documentId);

    Optional<DocumentPage> findByDocument_IdAndFilename(
            UUID documentId,
            String filename
    );
    List<DocumentPage> findAllByDocument_Id(UUID documentId);
}
