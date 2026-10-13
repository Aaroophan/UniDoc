package com.unidoc.admin;

import com.unidoc.common.api.CommonDtoMapper;
import com.unidoc.request.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {
    private final AdminAccess access;
    private final DocumentRequestRepository requests;
    private final RequestReadQueries listings;

    public AdminDashboardService(AdminAccess access, DocumentRequestRepository requests, RequestReadQueries listings) {
        this.access = access;
        this.requests = requests;
        this.listings = listings;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(long userId) {
        access.requireActiveAdministrator(userId);
        Map<RequestStatus, Long> counts = requests.countGroupedByStatus().stream()
                .collect(Collectors.toMap(com.unidoc.request.query.RequestStatusCount::status,
                        com.unidoc.request.query.RequestStatusCount::count));
        Function<RequestStatus, Long> count = status -> counts.getOrDefault(status, 0L);
        Map<DeliveryMethod, Long> delivery = new EnumMap<>(DeliveryMethod.class);
        for (DeliveryMethod method : DeliveryMethod.values()) delivery.put(method, 0L);
        requests.countGroupedByDeliveryMethod().forEach(group -> delivery.put(group.deliveryMethod(), group.count()));

        var recent = requests.findAll(PageRequest.of(0, 10,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).getContent();
        Map<Long, String> registrations = listings.registrations(recent);
        return new AdminDashboardResponse(
                requests.count(),
                count.apply(RequestStatus.SUBMITTED) + count.apply(RequestStatus.UNDER_REVIEW)
                        + count.apply(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED),
                count.apply(RequestStatus.APPROVED), count.apply(RequestStatus.PROCESSING),
                count.apply(RequestStatus.REJECTED), count.apply(RequestStatus.READY),
                count.apply(RequestStatus.COMPLETED), delivery,
                requests.countGroupedByDocumentType(),
                recent.stream().map(row -> CommonDtoMapper.toRequestSummary(
                        row, registrations.get(row.getStudent().getId()))).toList());
    }
}
