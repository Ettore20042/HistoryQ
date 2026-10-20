package com.example.historyq.service;

import com.example.historyq.dto.TeamInvitationResponse;
import com.example.historyq.entity.Notification;
import com.example.historyq.entity.Team;
import com.example.historyq.entity.TeamInvitation;
import com.example.historyq.entity.TeamInvitationStatus;
import com.example.historyq.entity.TeamMember;
import com.example.historyq.entity.TeamRole;
import com.example.historyq.entity.User;
import com.example.historyq.repository.NotificationRepository;
import com.example.historyq.repository.TeamInvitationRepository;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TeamInvitationService {

    private static final long INVITATION_VALIDITY_DAYS = 7;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public TeamInvitationService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamInvitationRepository teamInvitationRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamInvitationRepository = teamInvitationRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public TeamInvitationResponse inviteUser(
            UUID teamId,
            String requestedEmail
    ) {
        User currentUser = getAuthenticatedUser();

        String email = requestedEmail.trim().toLowerCase(Locale.ROOT);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() ->
                        new NoSuchElementException("Team non trovato."));

        TeamMember inviterMembership = teamMemberRepository
                .findByTeamIdAndUserId(teamId, currentUser.getId())
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "Non sei membro di questo Team."
                        ));

        TeamRole role = inviterMembership.getRole();

        if (role != TeamRole.OWNER && role != TeamRole.ADMIN) {
            throw new AccessDeniedException(
                    "Solo OWNER e ADMIN possono invitare utenti."
            );
        }

        User invitedUser = userRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (invitedUser != null
                && teamMemberRepository.existsByTeamIdAndUserId(
                teamId, invitedUser.getId())) {
            throw new IllegalArgumentException(
                    "L'utente è già membro di questo Team."
            );
        }

        if (teamInvitationRepository
                .existsByTeamIdAndEmailIgnoreCaseAndStatus(
                        teamId,
                        email,
                        TeamInvitationStatus.PENDING
                )) {
            throw new IllegalArgumentException(
                    "Esiste già un invito pendente per questa email."
            );
        }

        TeamInvitation invitation = new TeamInvitation();
        invitation.setTeam(team);
        invitation.setEmail(email);
        invitation.setInvitedBy(currentUser);
        invitation.setStatus(TeamInvitationStatus.PENDING);
        invitation.setTokenHash(generateTokenHash());
        invitation.setExpiresAt(
                java.time.OffsetDateTime.now()
                        .plusDays(INVITATION_VALIDITY_DAYS)
        );

        TeamInvitation savedInvitation =
                teamInvitationRepository.save(invitation);

        if (invitedUser != null) {
            Notification notification = new Notification();
            notification.setUser(invitedUser);
            notification.setTitle("Invito a un Team");
            notification.setMessage(
                    currentUser.getUsername()
                            + " ti ha invitato nel Team \""
                            + team.getName()
                            + "\"."
            );
            notification.setType("TEAM_INVITATION");
            notification.setTeamInvitationId(savedInvitation.getId());

            notificationRepository.save(notification);
        }

        return new TeamInvitationResponse(
                savedInvitation.getId(),
                savedInvitation.getEmail(),
                savedInvitation.getStatus()
        );
    }

    private String generateTokenHash() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);

        String randomToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    randomToken.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Impossibile generare l'hash dell'invito.",
                    exception
            );
        }
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

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Utente autenticato non trovato."
                        ));
    }
}