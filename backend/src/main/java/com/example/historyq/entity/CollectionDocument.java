package com.example.historyq.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collection_documents")
public class CollectionDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "ragflow_doc_id", length = 255)
    private String ragflowDocId;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public String getRagflowDocId() { return ragflowDocId; }
    public void setRagflowDocId(String ragflowDocId) { this.ragflowDocId = ragflowDocId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(OffsetDateTime addedAt) { this.addedAt = addedAt; }
}
