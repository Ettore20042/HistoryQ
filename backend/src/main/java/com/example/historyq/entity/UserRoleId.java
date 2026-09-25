package com.example.historyq.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;

@Embeddable
public class UserRoleId implements Serializable {
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "role")
    private String role;

    public UserRoleId() {
    }

    public UserRoleId(UUID userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        if (!super.equals(object)) return false;

        UserRoleId that = (UserRoleId) object;
        return java.util.Objects.equals(getUserId(), that.getUserId()) && java.util.Objects.equals(getRole(), that.getRole());
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(getUserId());
        result = 31 * result + Objects.hashCode(getRole());
        return result;
    }
}