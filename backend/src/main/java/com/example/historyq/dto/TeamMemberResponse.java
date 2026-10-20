
package com.example.historyq.dto;

import com.example.historyq.entity.TeamRole;

import java.util.UUID;

public record TeamMemberResponse(
        UUID userId,
        String username,
        TeamRole role
) {}
