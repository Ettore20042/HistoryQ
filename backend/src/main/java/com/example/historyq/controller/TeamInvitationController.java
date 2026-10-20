package com.example.historyq.controller;

import com.example.historyq.dto.TeamInvitationRequest;
import com.example.historyq.dto.TeamInvitationResponse;
import com.example.historyq.service.TeamInvitationService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/teams/{teamId}/invitations")
public class TeamInvitationController {

    private final TeamInvitationService teamInvitationService;

    public TeamInvitationController(
            TeamInvitationService teamInvitationService
    ) {
        this.teamInvitationService = teamInvitationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamInvitationResponse inviteUser(
            @PathVariable UUID teamId,
            @Valid @RequestBody TeamInvitationRequest request
    ) {
        return teamInvitationService.inviteUser(
                teamId,
                request.email()
        );
    }
}