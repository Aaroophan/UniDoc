package com.unidoc.notification;

import com.unidoc.common.persistence.UniDocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface NotificationRepository extends UniDocRepository<Notification, Long> {

    Optional<Notification> findByIdAndRecipientId(Long notificationId, Long recipientUserId);

    Page<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadOrderByCreatedAtDescIdDesc(
            Long recipientUserId,
            boolean read,
            Pageable pageable
    );

    long countByRecipientIdAndReadFalse(Long recipientUserId);
}
