package com.unidoc.notification;

import com.unidoc.common.persistence.UniDocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends UniDocRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByIdAndRecipientId(Long notificationId, Long recipientUserId);

    Page<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadOrderByCreatedAtDescIdDesc(
            Long recipientUserId, boolean read, Pageable pageable
    );

    long countByRecipientIdAndReadFalse(Long recipientUserId);

    /** One recipient-scoped bulk SQL update. No per-row loading or unbounded reads. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.read = true, n.readAt = :when " +
            "where n.recipient.id = :recipientId and n.read = false")
    int markAllUnreadAsRead(@Param("recipientId") long recipientId, @Param("when") Instant when);
}
