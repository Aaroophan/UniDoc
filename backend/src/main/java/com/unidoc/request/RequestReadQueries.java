package com.unidoc.request;

import com.unidoc.common.api.CommonDtoMapper;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.RequestSummary;
import com.unidoc.common.exception.InvalidQueryParameterException;
import com.unidoc.common.web.QueryParameters;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Database-side staff/admin monitoring query and common page/registration mapping. */
@Component
public class RequestReadQueries {
    private final DocumentRequestRepository requests;
    private final StudentProfileRepository profiles;

    public RequestReadQueries(DocumentRequestRepository requests, StudentProfileRepository profiles) {
        this.requests = requests;
        this.profiles = profiles;
    }

    public PageResponse<RequestSummary> list(String q, String rawStatus, Long documentTypeId,
                                             String rawDelivery, LocalDate from, LocalDate to,
                                             LocalDate requiredDate, Integer page, Integer size,
                                             String sort, String direction) {
        if (documentTypeId != null && documentTypeId < 1) {
            throw new InvalidQueryParameterException("documentTypeId must be positive.");
        }
        QueryParameters.validateDateRange(from, to);
        String queryText = QueryParameters.freeText(q, 120);
        RequestStatus status = QueryParameters.enumValue(rawStatus, RequestStatus.class, "status");
        DeliveryMethod method = QueryParameters.enumValue(rawDelivery, DeliveryMethod.class, "deliveryMethod");
        Pageable pageable = stablePage(QueryParameters.pageable(page, size, sort, direction,
                DocumentRequestSortProperties.MAPPINGS, DocumentRequestSortProperties.DEFAULT, Sort.Direction.DESC));
        Page<DocumentRequest> result = requests.findAll(DocumentRequestSpecifications.forStaffOrAdmin(
                queryText, status, documentTypeId, method, from, to, requiredDate), pageable);
        Map<Long, String> registrations = registrations(result.getContent());
        return PageResponse.from(result, request -> CommonDtoMapper.toRequestSummary(
                request, registrations.get(request.getStudent().getId())));
    }

    public Map<Long, String> registrations(List<DocumentRequest> rows) {
        Set<Long> ids = rows.stream().map(request -> request.getStudent().getId()).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return profiles.findByUserIdIn(ids).stream().collect(Collectors.toMap(
                profile -> profile.getUser().getId(), StudentProfile::getRegistrationNumber,
                (first, ignored) -> first));
    }

    public static Pageable stablePage(Pageable pageable) {
        Sort.Order primary = pageable.getSort().iterator().next();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(primary.getDirection(), "id")));
    }
}
