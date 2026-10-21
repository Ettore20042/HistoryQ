package com.example.historyq.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamDocumentResponse(
        UUID id,
        String displayName,
        String originalName,
        String author,
        OffsetDateTime createdAt,
        String visibility,
        String status,
        String addedBy
) {
}
