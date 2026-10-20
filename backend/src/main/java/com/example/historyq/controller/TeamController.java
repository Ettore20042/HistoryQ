
        package com.example.historyq.controller;

import com.example.historyq.entity.Team;
import com.example.historyq.service.TeamService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.example.historyq.dto.TeamResponse;
import com.example.historyq.dto.TeamMemberResponse;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    public record CreateTeamRequest(
            @NotBlank
            @Size(max = 100)
            String name,

            @Size(max = 5000)
            String description
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(
            @Valid @RequestBody CreateTeamRequest request
    ) {
        return teamService.createTeam(
                request.name(),
                request.description()
        );
    }

    @GetMapping
    public List<TeamResponse> getMyTeams() {
        return teamService.getMyTeams();
    }

    @GetMapping("/{teamId}")
    public TeamResponse getTeamById(@PathVariable UUID teamId) {
        return teamService.getTeam(teamId);
    }


    @GetMapping("/{teamId}/members")
    public List<TeamMemberResponse> getTeamMembers(
            @PathVariable UUID teamId
    ) {
        return teamService.getTeamMembers(teamId);
    }

}
