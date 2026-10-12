package com.unidoc.request;

import com.unidoc.common.persistence.UniDocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface RequestStatusHistoryRepository extends UniDocRepository<RequestStatusHistory, Long> {

    Page<RequestStatusHistory> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId, Pageable pageable);

    /** Append-only per-request timeline for the detail contract. */
    List<RequestStatusHistory> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);
}
