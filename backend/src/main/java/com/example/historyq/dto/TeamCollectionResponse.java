package com.example.historyq.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamCollectionResponse(
        UUID id,
        String name,
        String description,
        String visibility,
        OffsetDateTime createdAt,
        String addedBy
) {
}
