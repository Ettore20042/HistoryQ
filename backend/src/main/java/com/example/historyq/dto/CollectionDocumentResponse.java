package com.example.historyq.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CollectionDocumentResponse(
        UUID id,
        String displayName,
        String description,
        String originalName,
        String historicalDate,
        String author,
        String archiveSource,
        int pageCount,
        String documentStatus,
        String collectionStatus,
        OffsetDateTime addedAt
) {}