package com.example.historyq.repository;

import com.example.historyq.entity.CollectionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;


public interface CollectionDocumentRepository extends JpaRepository<CollectionDocument, UUID> {
    boolean existsByCollectionIdAndDocumentId(UUID collectionId, UUID documentId);
    List<CollectionDocument> findAllByCollection_Id(UUID collectionId);

    @Modifying
    @Query("""
            update CollectionDocument c
            set c.status = 'PROCESSING'
            where c.id = :id and c.status = 'PENDING'
            """)
    int claimPending(@Param("id") UUID id);

}
