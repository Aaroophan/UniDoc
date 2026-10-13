package com.unidoc.admin;

import com.unidoc.common.api.PageResponse;
import com.unidoc.common.web.QueryParameters;
import com.unidoc.request.RequestReadQueries;
import com.unidoc.request.DocumentRequest;
import com.unidoc.request.DocumentRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class RequestReportService {
    private static final Logger log = LoggerFactory.getLogger(RequestReportService.class);
    private final AdminAccess access;
    private final DocumentRequestRepository requests;
    private final RequestReadQueries listings;

    public RequestReportService(AdminAccess access, DocumentRequestRepository requests, RequestReadQueries listings) {
        this.access = access;
        this.requests = requests;
        this.listings = listings;
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestReportRow> report(long userId, RequestReportFilter filter,
                                                   Integer page, Integer size, String sort, String direction) {
        access.requireActiveAdministrator(userId);
        Pageable pageable = RequestReadQueries.stablePage(QueryParameters.pageable(page, size,
                sort, direction, RequestReportSortProperties.MAPPINGS, "createdAt", Sort.Direction.DESC));
        Page<DocumentRequest> results = requests.findAll(filter.specification(), pageable);
        Map<Long, String> registrations = listings.registrations(results.getContent());
        return PageResponse.from(results, request -> toRow(request, registrations));
    }

    @Transactional(readOnly = true)
    public byte[] csv(long userId, RequestReportFilter filter, String sort, String direction) {
        access.requireActiveAdministrator(userId);
        // All matching rows (no in-memory pagination), deliberately scoped to the demo dataset.
        String property = QueryParameters.sortProperty(sort, RequestReportSortProperties.MAPPINGS, "createdAt");
        Sort.Direction ordering = QueryParameters.direction(direction, Sort.Direction.DESC);
        List<DocumentRequest> results = requests.findAll(filter.specification(),
                Sort.by(ordering, property).and(Sort.by(ordering, "id")));
        Map<Long, String> registrations = listings.registrations(results);
        byte[] bytes = SafeCsvWriter.write(results.stream().map(row -> toRow(row, registrations)).toList());
        log.info("Administrator CSV export completed actorUserId={} status={} deliveryMethod={} typeId={} from={} to={} rowCount={}",
                userId, filter.status(), filter.deliveryMethod(), filter.documentTypeId(),
                filter.submittedFrom(), filter.submittedTo(), results.size());
        return bytes;
    }

    private static RequestReportRow toRow(DocumentRequest request, Map<Long, String> registrations) {
        var owner = request.getStudent();
        return new RequestReportRow(request.getRequestNumber(), registrations.get(owner.getId()),
                owner.getFullName(), owner.getEmail(), request.getDocumentTypeNameSnapshot(),
                request.getPurpose(), request.getDeliveryMethod(), request.getRequiredDate(),
                request.getStatus(), request.getCreatedAt(), request.getUpdatedAt(), request.getCompletedAt());
    }
}
