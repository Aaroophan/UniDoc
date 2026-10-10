package com.unidoc.request;

import com.unidoc.common.persistence.UniDocRepository;

import java.util.Optional;

public interface IssuedDocumentRepository extends UniDocRepository<IssuedDocument, Long> {

    Optional<IssuedDocument> findByRequestId(Long requestId);

    boolean existsByRequestId(Long requestId);
}
