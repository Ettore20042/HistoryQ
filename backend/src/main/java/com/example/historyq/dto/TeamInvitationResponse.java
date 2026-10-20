package com.example.historyq.dto;

import com.example.historyq.entity.TeamInvitationStatus;
import java.util.UUID;

public record TeamInvitationResponse(
        UUID id,
        String email,
        TeamInvitationStatus status
) {
}