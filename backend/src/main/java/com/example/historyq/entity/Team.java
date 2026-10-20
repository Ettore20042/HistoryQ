package com.example.historyq.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY,optional = false) // Use LAZY fetching to avoid loading the entire User entity unless needed
    @JoinColumn(name="created_by", nullable = false)
    private User createdBy;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Team() {
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}