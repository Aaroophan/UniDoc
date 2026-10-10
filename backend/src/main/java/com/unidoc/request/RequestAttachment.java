package com.unidoc.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unidoc.common.persistence.CreatedEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

@Entity
@Table(
        name = "request_attachments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_attachments_stored_filename", columnNames = "stored_filename"),
                @UniqueConstraint(name = "uq_attachments_relative_path", columnNames = "relative_path")
        },
        indexes = {
                @Index(name = "ix_attachments_request_created", columnList = "request_id, created_at"),
                @Index(name = "ix_attachments_request_kind", columnList = "request_id, attachment_kind")
        },
        check = {
                @CheckConstraint(
                        name = "ck_attachments_kind",
                        constraint = "attachment_kind IN ('INITIAL_SUPPORT','ADDITIONAL_INFORMATION')"
                ),
                @CheckConstraint(name = "ck_attachments_size", constraint = "size_bytes > 0")
        }
)
public class RequestAttachment extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "request_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_attachments_request")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DocumentRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "uploaded_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_attachments_uploader")
    )
    private User uploadedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "attachment_kind", nullable = false, length = 40)
    private AttachmentKind attachmentKind;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 100)
    private String storedFilename;

    @Column(name = "relative_path", nullable = false, length = 500)
    private String relativePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected RequestAttachment() {
    }

    public RequestAttachment(
            DocumentRequest request,
            User uploadedBy,
            AttachmentKind attachmentKind,
            String originalFilename,
            String storedFilename,
            String relativePath,
            String contentType,
            long sizeBytes
    ) {
        this.request = Objects.requireNonNull(request);
        this.uploadedBy = Objects.requireNonNull(uploadedBy);
        this.attachmentKind = Objects.requireNonNull(attachmentKind);
        this.originalFilename = Objects.requireNonNull(originalFilename);
        this.storedFilename = Objects.requireNonNull(storedFilename);
        this.relativePath = Objects.requireNonNull(relativePath);
        this.contentType = Objects.requireNonNull(contentType);
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public DocumentRequest getRequest() {
        return request;
    }

    @JsonIgnore
    public User getUploadedBy() {
        return uploadedBy;
    }

    public AttachmentKind getAttachmentKind() {
        return attachmentKind;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
