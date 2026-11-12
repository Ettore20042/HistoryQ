package com.example.historyq;

import com.example.historyq.entity.Document;
import com.example.historyq.entity.Notification;
import com.example.historyq.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationTest {

    @Test
    void testSerializationWithUserAndDocument() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        user.setPasswordHash("supersecret_hash");

        Document doc = new Document();
        doc.setDisplayName("Test Doc");

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setDocument(doc);
        notification.setTitle("Doc ready");
        notification.setMessage("Your doc is ready");
        notification.setType("DOCUMENT_READY");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(notification);
        System.out.println("WITH RELATIONS: " + json);

        // Check if password hash is leaked or document object is serialized
        if (json.contains("passwordHash")) {
            System.out.println("LEAK DETECTED: passwordHash is serialized in Notification JSON!");
        }
        if (json.contains("\"document\":{")) {
            System.out.println("DOCUMENT OBJECT SERIALIZED: document is serialized in Notification JSON!");
        }
    }
}
