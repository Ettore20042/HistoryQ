package com.example.historyq.repository;

import com.example.historyq.entity.TeamMember;
import com.example.historyq.entity.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findByTeamId(UUID teamId);
    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);
    List<TeamMember> findByUserId(UUID userId);
    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);
    List<TeamMember> findByTeamIdAndRole(UUID teamId, TeamRole role);

}