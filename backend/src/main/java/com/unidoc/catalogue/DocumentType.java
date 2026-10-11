package com.unidoc.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unidoc.common.persistence.AuditableEntity;
import com.unidoc.common.persistence.Normalization;
import com.unidoc.user.User;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.util.Objects;

@Entity
@Table(
        name = "document_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_document_types_code", columnNames = "code"),
                @UniqueConstraint(name = "uq_document_types_name", columnNames = "name")
        },
        indexes = @Index(name = "ix_document_types_active_name", columnList = "active, name"),
        check = @CheckConstraint(
                name = "ck_document_types_delivery",
                constraint = "electronic_available = TRUE OR physical_available = TRUE"
        )
)
public class DocumentType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "supporting_documents_required", nullable = false)
    @ColumnDefault("false")
    private boolean supportingDocumentsRequired;

    @Column(name = "electronic_available", nullable = false)
    @ColumnDefault("true")
    private boolean electronicAvailable = true;

    @Column(name = "physical_available", nullable = false)
    @ColumnDefault("true")
    private boolean physicalAvailable = true;

    @Column(name = "collection_location", length = 255)
    private String collectionLocation;

    @Column(name = "collection_instructions", columnDefinition = "TEXT")
    private String collectionInstructions;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_document_types_created_by")
    )
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "updated_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_document_types_updated_by")
    )
    private User updatedBy;

    protected DocumentType() {
    }

    public DocumentType(
            String code,
            String name,
            String description,
            String requirements,
            boolean supportingDocumentsRequired,
            boolean electronicAvailable,
            boolean physicalAvailable,
            String collectionLocation,
            String collectionInstructions,
            User actor
    ) {
        this.code = Normalization.documentCode(code);
        this.name = Objects.requireNonNull(name).trim();
        this.description = Objects.requireNonNull(description).trim();
        this.requirements = Objects.requireNonNull(requirements).trim();
        this.supportingDocumentsRequired = supportingDocumentsRequired;
        this.electronicAvailable = electronicAvailable;
        this.physicalAvailable = physicalAvailable;
        this.collectionLocation = collectionLocation == null ? null : collectionLocation.trim();
        this.collectionInstructions = collectionInstructions == null ? null : collectionInstructions.trim();
        this.createdBy = Objects.requireNonNull(actor);
        this.updatedBy = actor;
    }

    /** Update only editable catalogue values; never rewrite the historical request snapshots. */
    public void replaceEditable(
            String code, String name, String description, String requirements,
            boolean supportingDocumentsRequired, boolean electronicAvailable, boolean physicalAvailable,
            String collectionLocation, String collectionInstructions, boolean active, User actor
    ) {
        this.code = Normalization.documentCode(code);
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.requirements = Objects.requireNonNull(requirements);
        this.supportingDocumentsRequired = supportingDocumentsRequired;
        this.electronicAvailable = electronicAvailable;
        this.physicalAvailable = physicalAvailable;
        this.collectionLocation = collectionLocation;
        this.collectionInstructions = collectionInstructions;
        this.active = active;
        this.updatedBy = Objects.requireNonNull(actor);
    }

    /** Repeat status updates leave both the row and its audit timestamp unchanged. */
    public boolean changeActive(boolean next, User actor) {
        if (active == next) return false;
        active = next;
        updatedBy = Objects.requireNonNull(actor);
        return true;
    }

    @PrePersist
    @PreUpdate
    void normalizeCode() {
        code = Normalization.documentCode(code);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getRequirements() {
        return requirements;
    }

    public boolean isSupportingDocumentsRequired() {
        return supportingDocumentsRequired;
    }

    public boolean isElectronicAvailable() {
        return electronicAvailable;
    }

    public boolean isPhysicalAvailable() {
        return physicalAvailable;
    }

    public String getCollectionLocation() {
        return collectionLocation;
    }

    public String getCollectionInstructions() {
        return collectionInstructions;
    }

    public boolean isActive() {
        return active;
    }

    @JsonIgnore
    public User getCreatedBy() {
        return createdBy;
    }

    @JsonIgnore
    public User getUpdatedBy() {
        return updatedBy;
    }
}
