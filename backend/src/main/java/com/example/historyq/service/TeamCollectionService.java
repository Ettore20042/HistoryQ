package com.example.historyq.service;

import com.example.historyq.dto.TeamCollectionResponse;
import com.example.historyq.entity.Collection;
import com.example.historyq.entity.Team;
import com.example.historyq.entity.TeamCollection;
import com.example.historyq.entity.TeamMember;
import com.example.historyq.entity.TeamRole;
import com.example.historyq.entity.User;
import com.example.historyq.repository.CollectionRepository;
import com.example.historyq.repository.TeamCollectionRepository;
import com.example.historyq.repository.TeamMemberRepository;
import com.example.historyq.repository.TeamRepository;
import com.example.historyq.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TeamCollectionService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamCollectionRepository teamCollectionRepository;
    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;

    public TeamCollectionService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamCollectionRepository teamCollectionRepository,
            CollectionRepository collectionRepository,
            UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamCollectionRepository = teamCollectionRepository;
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamCollectionResponse> shared(UUID teamId) {
        User user = user();
        member(teamId, user);
        return teamCollectionRepository.findByTeamId(teamId).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<TeamCollectionResponse> candidates(
            UUID teamId, String source, String query) {
        User user = user();
        member(teamId, user);
        String normalized = source == null ? "ALL" : source.toUpperCase(Locale.ROOT);
        String q = query == null ? "" : query.trim();
        List<Collection> collections;
        if ("PERSONAL".equals(normalized)) {
            collections = collectionRepository.findByOwnerId(user.getId());
        } else if ("PUBLIC".equals(normalized)) {
            collections = collectionRepository.findByVisibilityAndNameContainingIgnoreCase(
                    "PUBLIC", q);
        } else {
            collections = collectionRepository.findShareableForUser(user.getId(), q);
        }
        return collections.stream()
                .filter(collection -> !teamCollectionRepository
                        .existsByTeamIdAndCollectionId(teamId, collection.getId()))
                .map(collection -> response(collection, null)).toList();
    }

    @Transactional
    public List<TeamCollectionResponse> add(
            UUID teamId, List<UUID> collectionIds) {
        User user = user();
        member(teamId, user);
        if (collectionIds == null || collectionIds.isEmpty()) {
            throw new IllegalArgumentException("Seleziona almeno una collezione.");
        }
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team non trovato."));
        return collectionIds.stream().distinct()
                .map(id -> addOne(team, teamId, id, user))
                .map(this::response).toList();
    }

    @Transactional
    public void remove(UUID teamId, UUID collectionId) {
        User user = user();
        TeamMember membership = member(teamId, user);
        if (membership.getRole() != TeamRole.OWNER
                && membership.getRole() != TeamRole.ADMIN) {
            throw new AccessDeniedException(
                    "Solo OWNER e ADMIN possono rimuovere collezioni condivise.");
        }
        TeamCollection association = teamCollectionRepository
                .findByTeamIdAndCollectionId(teamId, collectionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Collezione condivisa non trovata."));
        teamCollectionRepository.delete(association);
    }

    public boolean canAccess(UUID teamId, UUID collectionId, User user) {
        return teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())
                && teamCollectionRepository.existsByTeamIdAndCollectionId(
                teamId, collectionId);
    }

    private TeamCollection addOne(Team team, UUID teamId, UUID id, User user) {
        Collection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Collezione non trovata."));
        boolean owned = collection.getOwner().getId().equals(user.getId());
        boolean publicCollection = "PUBLIC".equalsIgnoreCase(collection.getVisibility());
        if (!owned && !publicCollection) {
            throw new AccessDeniedException("Non puoi condividere questa collezione.");
        }
        if (teamCollectionRepository.existsByTeamIdAndCollectionId(teamId, id)) {
            throw new IllegalArgumentException(
                    "La collezione è già condivisa con questo Team.");
        }
        TeamCollection association = new TeamCollection();
        association.setTeam(team);
        association.setCollection(collection);
        association.setAddedBy(user);
        return teamCollectionRepository.save(association);
    }

    private TeamMember member(UUID teamId, User user) {
        if (!teamRepository.existsById(teamId)) {
            throw new NoSuchElementException("Team non trovato.");
        }
        return teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Non sei membro di questo Team."));
    }

    private TeamCollectionResponse response(TeamCollection association) {
        return response(association.getCollection(),
                association.getAddedBy() == null ? null
                        : association.getAddedBy().getUsername());
    }

    private TeamCollectionResponse response(Collection collection, String addedBy) {
        return new TeamCollectionResponse(collection.getId(), collection.getName(),
                collection.getDescription(), collection.getVisibility(),
                collection.getCreatedAt(), addedBy);
    }

    private User user() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Utente non autenticato.");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException(
                        "Utente autenticato non trovato."));
    }
}
