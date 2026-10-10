package com.unidoc.common.web;

import com.unidoc.common.exception.InvalidQueryParameterException;
import com.unidoc.request.RequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryParametersTest {

    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "createdAt",
            "studentName", "student.fullName"
    );

    @Test
    void createsDefaultPageableWithDocumentedDefaults() {
        Pageable pageable = QueryParameters.pageable(
                null,
                null,
                null,
                null,
                SORTS,
                "createdAt",
                Sort.Direction.DESC
        );

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void acceptsBoundaryPageSizesAndRejectsValuesOutsideThem() {
        assertThat(QueryParameters.size(1)).isEqualTo(1);
        assertThat(QueryParameters.size(100)).isEqualTo(100);
        assertThatThrownBy(() -> QueryParameters.size(0)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> QueryParameters.size(101)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> QueryParameters.page(-1)).isInstanceOf(InvalidQueryParameterException.class);
    }

    @Test
    void onlyAllowsMappedSortNamesAndAscDescDirections() {
        assertThat(QueryParameters.sortProperty("studentName", SORTS, "createdAt"))
                .isEqualTo("student.fullName");
        assertThat(QueryParameters.direction("ASC", Sort.Direction.DESC)).isEqualTo(Sort.Direction.ASC);
        assertThatThrownBy(() -> QueryParameters.sortProperty("passwordHash", SORTS, "createdAt"))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> QueryParameters.direction("sideways", Sort.Direction.DESC))
                .isInstanceOf(InvalidQueryParameterException.class);
    }

    @Test
    void validatesDatesFreeTextAndEnumsWithoutSilentFallback() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        QueryParameters.validateDateRange(day, day);
        assertThatThrownBy(() -> QueryParameters.validateDateRange(day.plusDays(1), day))
                .isInstanceOf(InvalidQueryParameterException.class);

        assertThat(QueryParameters.freeText("  UDR-1  ", 20)).isEqualTo("UDR-1");
        assertThat(QueryParameters.freeText("   ", 20)).isNull();
        assertThatThrownBy(() -> QueryParameters.freeText("123456", 5))
                .isInstanceOf(InvalidQueryParameterException.class);

        assertThat(QueryParameters.enumValue("ready", RequestStatus.class, "status"))
                .isEqualTo(RequestStatus.READY);
        assertThatThrownBy(() -> QueryParameters.enumValue("waiting", RequestStatus.class, "status"))
                .isInstanceOf(InvalidQueryParameterException.class);
    }
}
