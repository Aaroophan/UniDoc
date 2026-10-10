package com.unidoc.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@MappedSuperclass
public abstract class AuditableEntity extends CreatedEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
