package com.example.historyq.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.example.historyq.entity.User;
import com.example.historyq.entity.Team;
import com.example.historyq.entity.TeamInvitationStatus;
import com.example.historyq.entity.TeamRole;



@Entity
@Table(name = "team_invitations", uniqueConstraints = @UniqueConstraint(name = "team_invitations_token_hask_key", columnNames = {"token_hash"}))

public class TeamInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    @Column(name = "token_hash", nullable = false, columnDefinition = "TEXT")
    private String tokenHash;


    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TeamInvitationStatus status = TeamInvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public TeamInvitationStatus getStatus() {
        return status;
    }

    public void setStatus(TeamInvitationStatus status) {
        this.status = status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(User invitedBy) {
        this.invitedBy = invitedBy;
    }

    public TeamInvitation() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}