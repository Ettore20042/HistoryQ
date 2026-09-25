package com.example.historyq.repository;

import com.example.historyq.entity.CollectionMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CollectionMemberRepository extends JpaRepository<CollectionMember, UUID> {
}
