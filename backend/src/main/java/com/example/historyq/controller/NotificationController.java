package com.example.historyq.controller;

import com.example.historyq.entity.User;
import com.example.historyq.repository.UserRepository;
import com.example.historyq.service.NotificationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final UserRepository userService;
    public NotificationController(NotificationService notificationService, UserRepository userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }




    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED);
        }

        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utente non trovato"));
    }
    @GetMapping
    public ResponseEntity<?> getNotifications(
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(
                notificationService.getNotifications(user));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(
                Map.of("count",
                        notificationService.getUnreadCount(user)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable UUID id,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        try {
            notificationService.markAsRead(id, user);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Notifica non trovata");
        }
    }

    @PatchMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        notificationService.markAllAsRead(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/accept-invitation")
    public ResponseEntity<?> acceptInvitation(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(
                notificationService.acceptInvitation(id, user));
    }

    @PostMapping("/{id}/decline-invitation")
    public ResponseEntity<?> declineInvitation(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(
                notificationService.declineInvitation(id, user));
    }
}