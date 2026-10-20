package com.example.historyq.repository;

import com.example.historyq.entity.TeamCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;


public interface TeamCollectionRepository extends JpaRepository<TeamCollection, UUID> {
    List<TeamCollection> findByTeamId(UUID teamId);
    Optional<TeamCollection> findByTeamIdAndCollectionId(UUID teamId, UUID collectionId);
    boolean existsByTeamIdAndCollectionId(UUID teamId, UUID collectionId);

}
