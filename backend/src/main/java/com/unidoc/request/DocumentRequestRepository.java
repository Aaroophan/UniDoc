package com.unidoc.request;

import com.unidoc.common.persistence.UniDocRepository;
import com.unidoc.request.query.DeliveryMethodCount;
import com.unidoc.request.query.DocumentTypeRequestCount;
import com.unidoc.request.query.RequestStatusCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentRequestRepository
        extends UniDocRepository<DocumentRequest, Long>, JpaSpecificationExecutor<DocumentRequest> {

    Optional<DocumentRequest> findByRequestNumber(String requestNumber);

    Optional<DocumentRequest> findByIdAndStudentId(Long requestId, Long studentUserId);

    Page<DocumentRequest> findByStudentId(Long studentUserId, Pageable pageable);

    Page<DocumentRequest> findByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select new com.unidoc.request.query.RequestStatusCount(request.status, count(request))
            from DocumentRequest request
            group by request.status
            """)
    List<RequestStatusCount> countGroupedByStatus();

    @Query("""
            select new com.unidoc.request.query.DeliveryMethodCount(request.deliveryMethod, count(request))
            from DocumentRequest request
            group by request.deliveryMethod
            """)
    List<DeliveryMethodCount> countGroupedByDeliveryMethod();

    @Query("""
            select new com.unidoc.request.query.DocumentTypeRequestCount(
                request.documentType.id,
                request.documentTypeNameSnapshot,
                count(request)
            )
            from DocumentRequest request
            group by request.documentType.id, request.documentTypeNameSnapshot
            order by count(request) desc, request.documentTypeNameSnapshot asc
            """)
    List<DocumentTypeRequestCount> countGroupedByDocumentType();
}
