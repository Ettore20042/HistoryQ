package com.example.historyq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "original_name", nullable = false, length = 512)
    private String originalName;

    @Column(name = "stored_filename", nullable = false, length = 512)
    private String storedFilename; //questo è il nome del file salvato nel filesystem o nel bucket S3 deciso dal DocumentService

    @Column(name = "file_type", nullable = false, length = 64)
    private String fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @ManyToOne
    @JoinColumn(name = "uploaded_by"/*, nullable = false*/)
    private User uploadedBy;

    @Column(name = "historical_date")
    private LocalDate historicalDate;

    @Column(length = 255)
    private String author;

    @Column(name = "archive_source", length = 512)
    private String archiveSource;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Column(name = "ragflow_dataset_id", length = 128)
    private String ragflowDatasetId;

    @Column(name = "ragflow_chat_id", length = 128)
    private String ragflowChatId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    @Column(name = "progress", nullable = false)
    private int progress;

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getRagflowDatasetId() {
        return ragflowDatasetId;
    }

    public void setRagflowDatasetId(String ragflowDatasetId) {
        this.ragflowDatasetId = ragflowDatasetId;
    }

    public String getRagflowChatId() {
        return ragflowChatId;
    }

    public void setRagflowChatId(String ragflowChatId) {
        this.ragflowChatId = ragflowChatId;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "storage_path", length = 1024)
    private String storagePath;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public User getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(User uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDate getHistoricalDate() { return historicalDate; }
    public void setHistoricalDate(LocalDate historicalDate) { this.historicalDate = historicalDate; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getArchiveSource() { return archiveSource; }
    public void setArchiveSource(String archiveSource) { this.archiveSource = archiveSource; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
