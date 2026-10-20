package com.example.historyq.dto;

import com.example.historyq.entity.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicDocumentResponse(
        UUID id,
        String originalName,
        String fileType,
        long fileSizeBytes,
        int pageCount,
        String status,
        int progress,
        OffsetDateTime createdAt,
        String createdBy,
        String description,
        String displayName,
        String historicalDate,
        String author,
        String archiveSource,
        String visibility
) {
    public static PublicDocumentResponse from(Document document) {
        return new PublicDocumentResponse(
                document.getId(), document.getOriginalName(), document.getFileType(),
                document.getFileSizeBytes(), document.getPageCount(), document.getStatus(),
                document.getProgress(), document.getCreatedAt(), document.getCreatedBy(),
                document.getDescription(), document.getDisplayName(), document.getHistoricalDate(),
                document.getAuthor(), document.getArchiveSource(), document.getVisibility());
    }
}
