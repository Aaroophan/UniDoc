package com.unidoc.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unidoc.common.persistence.CreatedEntity;
import com.unidoc.user.User;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

@Entity
@Table(
        name = "request_status_history",
        indexes = {
                @Index(name = "ix_history_request_created", columnList = "request_id, created_at, id"),
                @Index(name = "ix_history_actor_created", columnList = "changed_by_user_id, created_at")
        }
)
public class RequestStatusHistory extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "request_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_history_request")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DocumentRequest request;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "previous_status", length = 50)
    private RequestStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "new_status", nullable = false, length = 50)
    private RequestStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "changed_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_history_actor")
    )
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    protected RequestStatusHistory() {
    }

    public RequestStatusHistory(
            DocumentRequest request,
            RequestStatus previousStatus,
            RequestStatus newStatus,
            User changedBy,
            String note
    ) {
        this.request = Objects.requireNonNull(request);
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus);
        this.changedBy = Objects.requireNonNull(changedBy);
        this.note = note == null ? null : note.trim();
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public DocumentRequest getRequest() {
        return request;
    }

    public RequestStatus getPreviousStatus() {
        return previousStatus;
    }

    public RequestStatus getNewStatus() {
        return newStatus;
    }

    @JsonIgnore
    public User getChangedBy() {
        return changedBy;
    }

    public String getNote() {
        return note;
    }
}
