package com.example.historyq.repository;

import com.example.historyq.entity.UserRole;
import com.example.historyq.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    java.util.List<UserRole> findAllByIdUserId(java.util.UUID userId);
}
