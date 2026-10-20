package com.example.historyq.dto;

public record CreateCollectionRequest(
        String name,
        String description
) {}