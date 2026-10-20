package com.example.historyq.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CollectionResponse(
        UUID id,
        String name,
        String description,
        String visibility,
        OffsetDateTime createdAt
) {}