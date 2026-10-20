
package com.example.historyq.repository;

import com.example.historyq.entity.TeamInvitation;
import com.example.historyq.entity.TeamInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamInvitationRepository
        extends JpaRepository<TeamInvitation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from TeamInvitation invitation where invitation.id = :id")
    Optional<TeamInvitation> findByIdForUpdate(@Param("id") UUID id);

    Optional<TeamInvitation> findByTokenHash(String tokenHash);

    List<TeamInvitation> findByTeamId(UUID teamId);

    List<TeamInvitation> findByTeamIdAndStatus(
            UUID teamId,
            TeamInvitationStatus status
    );

    List<TeamInvitation> findByEmailIgnoreCaseAndStatus(
            String email,
            TeamInvitationStatus status
    );

    boolean existsByTeamIdAndEmailIgnoreCaseAndStatus(
            UUID teamId,
            String email,
            TeamInvitationStatus status
    );
}