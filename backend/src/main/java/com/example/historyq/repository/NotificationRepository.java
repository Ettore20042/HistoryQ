package com.example.historyq.repository;
import com.example.historyq.entity.Notification;
import com.example.historyq.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    long countByUserAndReadFalse(User user);
    Optional<Notification> findByIdAndUser(UUID id, User user);
    List<Notification> findByUserAndReadFalse(User user);
    void deleteAllByUser(User user);

    boolean existsByUserIdAndTeamInvitationId(
            UUID userId,
            UUID teamInvitationId
    );
}
