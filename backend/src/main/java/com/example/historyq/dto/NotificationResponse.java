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
        String documentName,
        UUID teamId,
        String teamName,
        String invitedByUsername,
        String invitationStatus
) {
    public static NotificationResponse from(Notification notification) {
        String documentName = notification.getDocument() != null
                ? notification.getDocument().getDisplayName()
                : null;
        String teamName = null;
        UUID teamId = null;
        String invitedByUsername = null;
        String invitationStatus = null;

        if (notification.getTeamInvitation() != null) {
            teamName = notification.getTeamInvitation().getTeam().getName();
            teamId = notification.getTeamInvitation().getTeam().getId();
            invitedByUsername =
                    notification.getTeamInvitation().getInvitedBy().getUsername();
            invitationStatus =
                    notification.getTeamInvitation().getStatus().name();
        }

        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead(),
                notification.getCreatedAt(),
                documentName,
                teamId,
                teamName,
                invitedByUsername,
                invitationStatus
        );
    }
}