package com.unidoc.request;

import com.unidoc.common.persistence.UniDocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;

public interface RequestAttachmentRepository extends UniDocRepository<RequestAttachment, Long> {

    Optional<RequestAttachment> findByIdAndRequestId(Long attachmentId, Long requestId);

    Page<RequestAttachment> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId, Pageable pageable);

    /** Only children of the owner-authorized request, ordered for detail display. */
    List<RequestAttachment> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);
}
