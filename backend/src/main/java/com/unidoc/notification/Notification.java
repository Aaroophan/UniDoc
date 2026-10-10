package com.unidoc.notification;

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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "ix_notifications_recipient_created", columnList = "recipient_user_id, created_at DESC"),
                @Index(
                        name = "ix_notifications_recipient_read_created",
                        columnList = "recipient_user_id, is_read, created_at DESC"
                )
        }
)
public class Notification extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipient_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_notifications_recipient")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "action_path", length = 255)
    private String actionPath;

    @Column(name = "is_read", nullable = false)
    @ColumnDefault("false")
    private boolean read;

    @Column(name = "read_at", columnDefinition = "DATETIME(6)")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(
            User recipient,
            NotificationType type,
            String title,
            String message,
            String actionPath
    ) {
        this.recipient = Objects.requireNonNull(recipient);
        this.type = Objects.requireNonNull(type);
        this.title = Objects.requireNonNull(title).trim();
        this.message = Objects.requireNonNull(message).trim();
        this.actionPath = actionPath;
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public User getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getActionPath() {
        return actionPath;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
