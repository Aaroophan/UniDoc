package com.unidoc.admin;

import com.unidoc.common.exception.InvalidQueryParameterException;
import com.unidoc.common.web.QueryParameters;
import com.unidoc.request.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** One immutable definition for paginated reports and complete CSV exports. */
public record RequestReportFilter(LocalDate submittedFrom, LocalDate submittedTo, RequestStatus status,
                                  Long documentTypeId, DeliveryMethod deliveryMethod) {
    public static RequestReportFilter parse(LocalDate from, LocalDate to, String status,
                                            Long typeId, String method) {
        QueryParameters.validateDateRange(from, to);
        if (typeId != null && typeId < 1) throw new InvalidQueryParameterException("documentTypeId must be positive.");
        return new RequestReportFilter(from, to,
                QueryParameters.enumValue(status, RequestStatus.class, "status"),
                typeId, QueryParameters.enumValue(method, DeliveryMethod.class, "deliveryMethod"));
    }

    public Specification<DocumentRequest> specification() {
        return DocumentRequestSpecifications.forStaffOrAdmin(null, status, documentTypeId,
                deliveryMethod, submittedFrom, submittedTo, null);
    }
}
