
        package com.example.historyq.service;

import com.example.historyq.dto.TeamResponse;
import com.example.historyq.entity.Team;
import com.example.historyq.entity.TeamMember;
import com.example.historyq.entity.TeamRole;
import com.example.historyq.entity.User;
import com.example.historyq.repository.TeamMemberRepository;
import com.example.historyq.repository.TeamRepository;
import com.example.historyq.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import com.example.historyq.dto.TeamMemberResponse;



@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TeamResponse createTeam(String name, String description) {
        User currentUser = getAuthenticatedUser();

        Team team = new Team();
        team.setName(name.trim());
        team.setDescription(description);
        team.setCreatedBy(currentUser);

        Team savedTeam = teamRepository.save(team);

        TeamMember ownerMember = new TeamMember();
        ownerMember.setTeam(savedTeam);
        ownerMember.setUser(currentUser);
        ownerMember.setRole(TeamRole.OWNER);

        teamMemberRepository.save(ownerMember);

        return toResponse(savedTeam);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams() {
        User currentUser = getAuthenticatedUser();

        return teamMemberRepository.findByUserId(currentUser.getId())
                .stream()
                .map(TeamMember::getTeam)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID teamId) {
        User currentUser = getAuthenticatedUser();

        boolean isMember = teamMemberRepository
                .existsByTeamIdAndUserId(teamId, currentUser.getId());

        if (!isMember) {
            throw new AccessDeniedException(
                    "Non sei membro di questo Team."
            );
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() ->
                        new NoSuchElementException("Team non trovato."));

        return toResponse(team);
    }

    private TeamResponse toResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getCreatedBy().getUsername()
        );
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().equals("anonymousUser")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Utente non autenticato."
            );
        }

        String username = authentication.getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Utente autenticato non trovato."
                        ));
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers(UUID teamId) {
        User currentUser = getAuthenticatedUser();

        boolean isMember = teamMemberRepository
                .existsByTeamIdAndUserId(teamId, currentUser.getId());

        if (!isMember) {
            throw new AccessDeniedException(
                    "Non sei membro di questo Team."
            );
        }

        // Verifica che il Team esista.
        teamRepository.findById(teamId)
                .orElseThrow(() ->
                        new NoSuchElementException("Team non trovato."));

        return teamMemberRepository.findByTeamId(teamId)
                .stream()
                .map(member -> new TeamMemberResponse(
                        member.getUser().getId(),
                        member.getUser().getUsername(),
                        member.getRole()
                ))
                .toList();
    }

}
