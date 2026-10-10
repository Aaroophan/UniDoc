package com.unidoc.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unidoc.common.persistence.AuditableEntity;
import com.unidoc.common.persistence.Normalization;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"),
        indexes = {
                @Index(name = "ix_users_role_status", columnList = "role, status"),
                @Index(name = "ix_users_created_at", columnList = "created_at")
        },
        check = {
                @CheckConstraint(name = "ck_users_role", constraint = "role IN ('STUDENT','STAFF','ADMIN')"),
                @CheckConstraint(name = "ck_users_status", constraint = "status IN ('ACTIVE','INACTIVE')")
        }
)
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "auth_version", nullable = false)
    @ColumnDefault("0")
    private int authVersion;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, UserRole role) {
        this(email, passwordHash, fullName, role, UserStatus.ACTIVE);
    }

    public User(String email, String passwordHash, String fullName, UserRole role, UserStatus status) {
        this.email = Normalization.email(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.fullName = Objects.requireNonNull(fullName).trim();
        this.role = Objects.requireNonNull(role);
        this.status = Objects.requireNonNull(status);
    }

    @PrePersist
    @PreUpdate
    void normalizeEmail() {
        email = Normalization.email(email);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    @JsonIgnore
    public int getAuthVersion() {
        return authVersion;
    }
}
