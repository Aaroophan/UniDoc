package com.unidoc.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unidoc.catalogue.DocumentType;
import com.unidoc.common.persistence.AuditableEntity;
import com.unidoc.user.User;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "document_requests",
        uniqueConstraints = @UniqueConstraint(name = "uq_document_requests_number", columnNames = "request_number"),
        indexes = {
                @Index(name = "ix_requests_student_created", columnList = "student_user_id, created_at DESC"),
                @Index(name = "ix_requests_status_created", columnList = "status, created_at DESC"),
                @Index(name = "ix_requests_type_created", columnList = "document_type_id, created_at DESC"),
                @Index(name = "ix_requests_delivery_status", columnList = "delivery_method, status"),
                @Index(name = "ix_requests_required_date", columnList = "required_date"),
                @Index(name = "ix_requests_created_at", columnList = "created_at")
        },
        check = {
                @CheckConstraint(
                        name = "ck_requests_delivery",
                        constraint = "delivery_method IN ('ELECTRONIC','PHYSICAL')"
                ),
                @CheckConstraint(
                        name = "ck_requests_status",
                        constraint = "status IN ('SUBMITTED','UNDER_REVIEW','ADDITIONAL_INFORMATION_REQUIRED',"
                                + "'APPROVED','REJECTED','PROCESSING','READY','COMPLETED')"
                )
        }
)
public class DocumentRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", length = 30)
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "student_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_requests_student")
    )
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "document_type_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_requests_document_type")
    )
    private DocumentType documentType;

    @Column(name = "document_type_name_snapshot", nullable = false, length = 150)
    private String documentTypeNameSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_method", nullable = false, length = 20)
    private DeliveryMethod deliveryMethod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    @ColumnDefault("'SUBMITTED'")
    private RequestStatus status = RequestStatus.SUBMITTED;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "required_date", nullable = false, columnDefinition = "DATE")
    private LocalDate requiredDate;

    @Column(name = "additional_information_message", columnDefinition = "TEXT")
    private String additionalInformationMessage;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "collection_location_snapshot", length = 255)
    private String collectionLocationSnapshot;

    @Column(name = "collection_instructions_snapshot", columnDefinition = "TEXT")
    private String collectionInstructionsSnapshot;

    @Column(name = "ready_at", columnDefinition = "DATETIME(6)")
    private Instant readyAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private Instant completedAt;

    @Column(name = "collection_date", columnDefinition = "DATE")
    private LocalDate collectionDate;

    @Column(name = "collection_note", columnDefinition = "TEXT")
    private String collectionNote;

    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private long version;

    protected DocumentRequest() {
    }

    public DocumentRequest(
            User student,
            DocumentType documentType,
            DeliveryMethod deliveryMethod,
            String purpose,
            LocalDate requiredDate
    ) {
        this.student = Objects.requireNonNull(student);
        this.documentType = Objects.requireNonNull(documentType);
        this.documentTypeNameSnapshot = documentType.getName();
        this.deliveryMethod = Objects.requireNonNull(deliveryMethod);
        this.purpose = Objects.requireNonNull(purpose).trim();
        this.requiredDate = Objects.requireNonNull(requiredDate);
        this.collectionLocationSnapshot = documentType.getCollectionLocation();
        this.collectionInstructionsSnapshot = documentType.getCollectionInstructions();
    }

    public void assignRequestNumber() {
        if (id == null) {
            throw new IllegalStateException("The request must be persisted before assigning its request number.");
        }
        if (requestNumber == null) {
            requestNumber = "UDR-%06d".formatted(id);
        }
    }

    public void updateStatus(RequestStatus newStatus) {
        status = Objects.requireNonNull(newStatus);
    }

    public Long getId() {
        return id;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    @JsonIgnore
    public User getStudent() {
        return student;
    }

    @JsonIgnore
    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentTypeNameSnapshot() {
        return documentTypeNameSnapshot;
    }

    public DeliveryMethod getDeliveryMethod() {
        return deliveryMethod;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getPurpose() {
        return purpose;
    }

    public LocalDate getRequiredDate() {
        return requiredDate;
    }

    public String getAdditionalInformationMessage() {
        return additionalInformationMessage;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getCollectionLocationSnapshot() {
        return collectionLocationSnapshot;
    }

    public String getCollectionInstructionsSnapshot() {
        return collectionInstructionsSnapshot;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public LocalDate getCollectionDate() {
        return collectionDate;
    }

    public String getCollectionNote() {
        return collectionNote;
    }

    public long getVersion() {
        return version;
    }
}
