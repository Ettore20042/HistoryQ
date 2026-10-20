package com.example.historyq.repository;

import com.example.historyq.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import com.example.historyq.entity.User;
import java.util.Optional;


public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByUploadedBy(User user);
    List<Document> findByUploadedByAndDisplayNameContainingIgnoreCase(
            User user, String displayName);
    Optional<Document> findByIdAndUploadedBy(UUID id, User user);
    List<Document> findByVisibilityOrderByDisplayNameAsc(String visibility);
    List<Document> findByVisibilityAndDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(
            String visibility,
            String displayName
    );

    @org.springframework.data.jpa.repository.Query("""
            select d from Document d
            where d.status = 'READY'
              and (d.visibility = 'PUBLIC' or d.uploadedBy.id = :userId)
              and (:query = '' or lower(d.displayName) like lower(concat('%', :query, '%')))
            order by d.displayName asc
            """)
    List<Document> findShareableForUser(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("query") String query
    );



}
