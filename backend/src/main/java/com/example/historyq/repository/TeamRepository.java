
        package com.example.historyq.repository;

import com.example.historyq.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
}
