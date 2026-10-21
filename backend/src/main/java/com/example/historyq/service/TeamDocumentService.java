package com.example.historyq.service;

import com.example.historyq.dto.TeamDocumentResponse;
import com.example.historyq.entity.Document;
import com.example.historyq.entity.Team;
import com.example.historyq.entity.TeamDocument;
import com.example.historyq.entity.TeamMember;
import com.example.historyq.entity.TeamRole;
import com.example.historyq.entity.User;
import com.example.historyq.repository.DocumentRepository;
import com.example.historyq.repository.TeamDocumentRepository;
import com.example.historyq.repository.TeamMemberRepository;
import com.example.historyq.repository.TeamRepository;
import com.example.historyq.repository.UserRepository;
import com.example.historyq.repository.DocumentPageRepository;
import com.example.historyq.entity.DocumentPage;
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
public class TeamDocumentService {

    private static final String READY = "READY";

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamDocumentRepository teamDocumentRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentPageRepository documentPageRepository;

    public TeamDocumentService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamDocumentRepository teamDocumentRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            DocumentPageRepository documentPageRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamDocumentRepository = teamDocumentRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.documentPageRepository = documentPageRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamDocumentResponse> getSharedDocuments(UUID teamId) {
        User user = getAuthenticatedUser();
        requireMember(teamId, user);
        return teamDocumentRepository.findByTeamId(teamId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamDocumentResponse> getCandidates(
            UUID teamId,
            String source,
            String query
    ) {
        User user = getAuthenticatedUser();
        requireMember(teamId, user);

        String normalizedSource = source == null
                ? "ALL"
                : source.trim().toUpperCase(Locale.ROOT);
        if (!normalizedSource.equals("ALL")
                && !normalizedSource.equals("PERSONAL")
                && !normalizedSource.equals("PUBLIC")) {
            throw new IllegalArgumentException("Origine documento non valida.");
        }

        String normalizedQuery = query == null ? "" : query.trim();
        List<Document> documents;
        if (normalizedSource.equals("PERSONAL")) {
            documents = normalizedQuery.isBlank()
                    ? documentRepository.findByUploadedBy(user)
                    : documentRepository
                    .findByUploadedByAndDisplayNameContainingIgnoreCase(user, normalizedQuery);
        } else if (normalizedSource.equals("PUBLIC")) {
            documents = normalizedQuery.isBlank()
                    ? documentRepository.findByVisibilityOrderByDisplayNameAsc("PUBLIC")
                    : documentRepository
                    .findByVisibilityAndDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(
                            "PUBLIC", normalizedQuery);
        } else {
            documents = documentRepository.findShareableForUser(
                    user.getId(), normalizedQuery);
        }

        return documents.stream()
                .filter(document -> READY.equals(document.getStatus()))
                .filter(document -> !teamDocumentRepository
                        .existsByTeamIdAndDocumentId(teamId, document.getId()))
                .map(document -> toResponse(document, null))
                .toList();
    }

    @Transactional
    public List<TeamDocumentResponse> addDocuments(
            UUID teamId,
            List<UUID> documentIds
    ) {
        User user = getAuthenticatedUser();
        requireMember(teamId, user);
        if (documentIds == null || documentIds.isEmpty()) {
            throw new IllegalArgumentException("Seleziona almeno un documento.");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team non trovato."));

        return documentIds.stream()
                .distinct()
                .map(documentId -> addDocument(team, teamId, documentId, user))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void removeDocument(UUID teamId, UUID documentId) {
        User user = getAuthenticatedUser();
        TeamMember membership = requireMember(teamId, user);
        if (membership.getRole() != TeamRole.OWNER
                && membership.getRole() != TeamRole.ADMIN) {
            throw new AccessDeniedException(
                    "Solo OWNER e ADMIN possono rimuovere documenti condivisi."
            );
        }

        TeamDocument association = teamDocumentRepository
                .findByTeamIdAndDocumentId(teamId, documentId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Documento condiviso non trovato."));
        teamDocumentRepository.delete(association);
    }

    @Transactional(readOnly = true)
    public Document getSharedDocument(UUID teamId, UUID documentId) {
        User user = getAuthenticatedUser();
        requireMember(teamId, user);
        return teamDocumentRepository.findByTeamIdAndDocumentId(teamId, documentId)
                .map(TeamDocument::getDocument)
                .orElseThrow(() -> new NoSuchElementException(
                        "Documento non condiviso con questo Team."));
    }

    @Transactional(readOnly = true)
    public DocumentPage getSharedPage(UUID teamId, UUID documentId, String filename) {
        getSharedDocument(teamId, documentId);
        if (!filename.matches("[A-Za-z0-9._-]+") || filename.contains("..")) {
            throw new IllegalArgumentException("Nome pagina non valido.");
        }
        return documentPageRepository.findByDocument_IdAndFilename(documentId, filename)
                .orElseThrow(() -> new NoSuchElementException("Pagina non trovata."));
    }

    private TeamDocument addDocument(
            Team team,
            UUID teamId,
            UUID documentId,
            User user
    ) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Documento non trovato."));
        boolean owned = document.getUploadedBy() != null
                && document.getUploadedBy().getId().equals(user.getId());
        boolean publicDocument = "PUBLIC".equals(document.getVisibility());
        if (!owned && !publicDocument) {
            throw new AccessDeniedException(
                    "Non puoi condividere questo documento."
            );
        }
        if (!READY.equals(document.getStatus())) {
            throw new IllegalArgumentException(
                    "Il documento non è ancora disponibile."
            );
        }
        if (teamDocumentRepository.existsByTeamIdAndDocumentId(teamId, documentId)) {
            throw new IllegalArgumentException(
                    "Il documento è già condiviso con questo Team."
            );
        }

        TeamDocument association = new TeamDocument();
        association.setTeam(team);
        association.setDocument(document);
        association.setAddedBy(user);
        return teamDocumentRepository.save(association);
    }

    private TeamMember requireMember(UUID teamId, User user) {
        if (!teamRepository.existsById(teamId)) {
            throw new NoSuchElementException("Team non trovato.");
        }
        return teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Non sei membro di questo Team."));
    }

    private TeamDocumentResponse toResponse(TeamDocument association) {
        return toResponse(association.getDocument(),
                association.getAddedBy() == null
                        ? null
                        : association.getAddedBy().getUsername());
    }

    private TeamDocumentResponse toResponse(Document document, String addedBy) {
        return new TeamDocumentResponse(
                document.getId(),
                document.getDisplayName(),
                document.getOriginalName(),
                document.getAuthor(),
                document.getCreatedAt(),
                document.getVisibility(),
                document.getStatus(),
                addedBy
        );
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Utente non autenticato.");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException(
                        "Utente autenticato non trovato."));
    }
}
