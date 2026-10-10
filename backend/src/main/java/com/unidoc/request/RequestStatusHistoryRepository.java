package com.unidoc.request;

import com.unidoc.common.persistence.UniDocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RequestStatusHistoryRepository extends UniDocRepository<RequestStatusHistory, Long> {

    Page<RequestStatusHistory> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId, Pageable pageable);
}
