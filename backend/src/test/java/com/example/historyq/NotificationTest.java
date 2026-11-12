package com.example.historyq;

import com.example.historyq.entity.Document;
import com.example.historyq.entity.Notification;
import com.example.historyq.entity.User;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationTest {

    @Test
    void testNotificationFieldsAndDefaultValues() {
        Notification notification = new Notification();
        User user = new User();
        Document document = new Document();

        notification.setUser(user);
        notification.setDocument(document);
        notification.setTitle("Doc ready");
        notification.setMessage("Your doc is ready");
        notification.setType("DOCUMENT_READY");
        notification.setRead(true);

        assertEquals(user, notification.getUser());
        assertEquals(document, notification.getDocument());
        assertEquals("Doc ready", notification.getTitle());
        assertEquals("Your doc is ready", notification.getMessage());
        assertEquals("DOCUMENT_READY", notification.getType());
        assertTrue(notification.isRead());
        assertFalse(new Notification().isRead());
        assertNotNull(notification.getCreatedAt());
    }

    @Test
    void testNotificationCanBeMarkedAsReadAndAssignedAnInvitation() {
        User user = new User();
        Notification notification = new Notification();
        notification.setUser(user);
        UUID invitationId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().minusMinutes(5);

        notification.setTeamInvitationId(invitationId);
        notification.setCreatedAt(createdAt);
        notification.setRead(true);

        assertEquals(user, notification.getUser());
        assertEquals(invitationId, notification.getTeamInvitationId());
        assertEquals(createdAt, notification.getCreatedAt());
        assertTrue(notification.isRead());
    }
}
