package com.example.historyq.dto;

import java.util.UUID;

public record TeamResponse(
        UUID id,
        String name,
        String description,
        String imageUrl
) {
}