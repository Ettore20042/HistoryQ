
package com.example.historyq.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        name = "document_pages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_document_pages_document_filename",
                        columnNames = {"document_id", "filename"}
                )
        }
)
public class DocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private String filename;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Column(name = "ragflow_document_id")
    private String ragflowDocumentId;

    public String getRagflowDocumentId() {
        return ragflowDocumentId;
    }

    public void setRagflowDocumentId(String ragflowDocumentId) {
        this.ragflowDocumentId = ragflowDocumentId;
    }

    public UUID getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }
}
