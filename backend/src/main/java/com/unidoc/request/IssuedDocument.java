package com.unidoc.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unidoc.common.persistence.CreatedEntity;
import com.unidoc.user.User;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "issued_documents",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_issued_documents_request", columnNames = "request_id"),
                @UniqueConstraint(name = "uq_issued_documents_stored_filename", columnNames = "stored_filename"),
                @UniqueConstraint(name = "uq_issued_documents_relative_path", columnNames = "relative_path")
        },
        check = {
                @CheckConstraint(name = "ck_issued_documents_content_type", constraint = "content_type = 'application/pdf'"),
                @CheckConstraint(name = "ck_issued_documents_size", constraint = "size_bytes > 0")
        }
)
public class IssuedDocument extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "request_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_issued_documents_request")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DocumentRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "uploaded_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_issued_documents_uploader")
    )
    private User uploadedBy;

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

    @Column(name = "download_count", nullable = false, columnDefinition = "INT UNSIGNED")
    @ColumnDefault("0")
    private int downloadCount;

    @Column(name = "first_downloaded_at", columnDefinition = "DATETIME(6)")
    private Instant firstDownloadedAt;

    @Column(name = "last_downloaded_at", columnDefinition = "DATETIME(6)")
    private Instant lastDownloadedAt;

    protected IssuedDocument() {
    }

    public IssuedDocument(
            DocumentRequest request,
            User uploadedBy,
            String originalFilename,
            String storedFilename,
            String relativePath,
            String contentType,
            long sizeBytes
    ) {
        this.request = Objects.requireNonNull(request);
        this.uploadedBy = Objects.requireNonNull(uploadedBy);
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

    public int getDownloadCount() {
        return downloadCount;
    }

    public Instant getFirstDownloadedAt() {
        return firstDownloadedAt;
    }

    public Instant getLastDownloadedAt() {
        return lastDownloadedAt;
    }
}
