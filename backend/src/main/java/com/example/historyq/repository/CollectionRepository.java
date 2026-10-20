package com.example.historyq.repository;

import com.example.historyq.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {
    List<Collection> findByOwnerId(UUID ownerId);
    List<Collection> findByVisibilityAndNameContainingIgnoreCase(
            String visibility, String name);

    @Query("""
            select c from Collection c
            where (c.visibility = 'PUBLIC' or c.owner.id = :userId)
              and lower(c.name) like lower(concat('%', :query, '%'))
            order by c.name asc
            """)
    List<Collection> findShareableForUser(@Param("userId") UUID userId, @Param("query") String query);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Collection c where c.id = :id")
    java.util.Optional<Collection> findByIdForUpdate(@Param("id") UUID id);

}
