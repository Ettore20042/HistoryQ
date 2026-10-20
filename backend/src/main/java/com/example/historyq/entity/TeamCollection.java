package com.example.historyq.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "team_collections",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_team_collections_team_collection",
        columnNames = {"team_id", "collection_id"}
    )
)
public class TeamCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by", nullable = false)
    private User addedBy;

    public java.util.UUID getId() {
        return id;
    }

    public User getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(User addedBy) {
        this.addedBy = addedBy;
    }

    public void setId(java.util.UUID id) {
        this.id = id;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }
}