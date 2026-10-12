package com.unidoc.request;

import com.unidoc.common.persistence.UniDocRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IssuedDocumentRepository extends UniDocRepository<IssuedDocument, Long> {

    Optional<IssuedDocument> findByRequestId(Long requestId);

    boolean existsByRequestId(Long requestId);

    /** Atomic increment prevents two parallel later downloads from losing a count. */
    @Modifying(flushAutomatically = true)
    @Query("update IssuedDocument d set d.downloadCount = d.downloadCount + 1, "
            + "d.firstDownloadedAt = coalesce(d.firstDownloadedAt, :when), "
            + "d.lastDownloadedAt = :when where d.id = :id")
    int recordStudentDownload(@Param("id") Long id, @Param("when") Instant when);
}
