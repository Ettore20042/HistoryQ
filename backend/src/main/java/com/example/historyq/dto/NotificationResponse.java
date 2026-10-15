package com.example.historyq.dto;

import com.example.historyq.entity.Notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String message,
        String type,
        boolean read,
        OffsetDateTime createdAt,
        String documentName
) {
    public static NotificationResponse from(Notification notification) {
        String documentName = notification.getDocument() != null
                ? notification.getDocument().getDisplayName()
                : null;

        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead(),
                notification.getCreatedAt(),
                documentName
        );
    }
}