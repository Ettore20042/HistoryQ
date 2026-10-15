
package com.example.historyq.service;

import com.example.historyq.entity.Document;
import com.example.historyq.entity.Notification;
import com.example.historyq.entity.User;
import com.example.historyq.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.historyq.dto.NotificationResponse;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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
}
