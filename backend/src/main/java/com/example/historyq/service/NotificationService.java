
package com.example.historyq.service;

import com.example.historyq.entity.Document;
import com.example.historyq.entity.Notification;
import com.example.historyq.entity.TeamInvitation;
import com.example.historyq.entity.TeamInvitationStatus;
import com.example.historyq.entity.TeamMember;
import com.example.historyq.entity.User;
import com.example.historyq.repository.NotificationRepository;
import com.example.historyq.repository.TeamInvitationRepository;
import com.example.historyq.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.historyq.dto.NotificationResponse;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final TeamMemberRepository teamMemberRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            TeamInvitationRepository teamInvitationRepository,
            TeamMemberRepository teamMemberRepository) {
        this.notificationRepository = notificationRepository;
        this.teamInvitationRepository = teamInvitationRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Transactional
    public void notifyDocumentReady(Document document) {
        createNotification(
                document,
                "Documento pronto",
                "Il documento è pronto per essere consultato.",
                "DOCUMENT_READY"
        );
    }

    @Transactional
    public void notifyDocumentError(Document document) {
        createNotification(
                document,
                "Errore di elaborazione",
                "Non è stato possibile completare l'elaborazione del documento.",
                "DOCUMENT_ERROR"
        );
    }

    @Transactional
    public void createNotification(
            Document document,
            String title,
            String message,
            String type) {

        User user = document.getUploadedBy();

        if (user == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setDocument(document);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(User user) {
        return notificationRepository
                .findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }
    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Transactional
    public void markAsRead(UUID id, User user) {
        Notification notification = notificationRepository
                .findByIdAndUser(id, user)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Notifica non trovata"));

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> notifications =
                notificationRepository.findByUserAndReadFalse(user);

        for (Notification notification : notifications) {
            notification.setRead(true);
        }

        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public NotificationResponse acceptInvitation(UUID notificationId, User user) {
        TeamInvitation invitation =
                getPendingInvitation(notificationId, user);

        verifyInvitationRecipient(invitation, user);
        if (teamMemberRepository.existsByTeamIdAndUserId(
                invitation.getTeam().getId(), user.getId())) {
            throw new IllegalArgumentException(
                    "Sei già membro di questo Team.");
        }

        TeamMember member = new TeamMember();
        member.setTeam(invitation.getTeam());
        member.setUser(user);
        member.setRole(com.example.historyq.entity.TeamRole.MEMBER);
        teamMemberRepository.save(member);

        invitation.setStatus(TeamInvitationStatus.ACCEPTED);
        Notification notification = getInvitationNotification(notificationId, user);
        notification.setRead(true);

        return NotificationResponse.from(notification);
    }

    @Transactional
    public NotificationResponse declineInvitation(UUID notificationId, User user) {
        TeamInvitation invitation =
                getPendingInvitation(notificationId, user);

        verifyInvitationRecipient(invitation, user);
        invitation.setStatus(TeamInvitationStatus.DECLINED);

        Notification notification = getInvitationNotification(notificationId, user);
        notification.setRead(true);
        return NotificationResponse.from(notification);
    }

    private TeamInvitation getPendingInvitation(UUID notificationId, User user) {
        Notification notification = getInvitationNotification(notificationId, user);

        if (!"TEAM_INVITATION".equals(notification.getType())
                || notification.getTeamInvitationId() == null) {
            throw new IllegalArgumentException(
                    "La notifica non contiene un invito valido.");
        }

        TeamInvitation invitation = teamInvitationRepository
                .findByIdForUpdate(notification.getTeamInvitationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invito non trovato."));

        if (invitation.getStatus() != TeamInvitationStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Questo invito è già stato gestito.");
        }
        return invitation;
    }

    private Notification getInvitationNotification(
            UUID notificationId,
            User user
    ) {
        return notificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Notifica non trovata."));
    }

    private void verifyInvitationRecipient(
            TeamInvitation invitation,
            User user
    ) {
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new AccessDeniedException(
                    "Questo invito non è destinato al tuo account.");
        }
    }
}
