package com.unidoc.common.web;

import com.unidoc.common.exception.InvalidQueryParameterException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

public final class QueryParameters {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private QueryParameters() {
    }

    public static Pageable pageable(
            Integer requestedPage,
            Integer requestedSize,
            String requestedSort,
            String requestedDirection,
            Map<String, String> allowedSorts,
            String defaultSort,
            Sort.Direction defaultDirection
    ) {
        int page = page(requestedPage);
        int size = size(requestedSize);
        String property = sortProperty(requestedSort, allowedSorts, defaultSort);
        Sort.Direction direction = direction(requestedDirection, defaultDirection);
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    public static int page(Integer requestedPage) {
        int value = requestedPage == null ? DEFAULT_PAGE : requestedPage;
        if (value < 0) {
            throw new InvalidQueryParameterException("page must be 0 or greater.");
        }
        return value;
    }

    public static int size(Integer requestedSize) {
        int value = requestedSize == null ? DEFAULT_SIZE : requestedSize;
        if (value < 1 || value > MAX_SIZE) {
            throw new InvalidQueryParameterException("size must be between 1 and 100.");
        }
        return value;
    }

    public static Sort.Direction direction(String requestedDirection, Sort.Direction defaultDirection) {
        if (requestedDirection == null || requestedDirection.isBlank()) {
            return defaultDirection;
        }

        return switch (requestedDirection.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidQueryParameterException("direction must be asc or desc.");
        };
    }

    public static String sortProperty(
            String requestedSort,
            Map<String, String> allowedSorts,
            String defaultSort
    ) {
        String externalName = requestedSort == null || requestedSort.isBlank()
                ? defaultSort
                : requestedSort.trim();
        String property = allowedSorts.get(externalName);
        if (property == null) {
            throw new InvalidQueryParameterException("sort contains an unsupported field.");
        }
        return property;
    }

    public static void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidQueryParameterException("from date cannot be after to date.");
        }
    }

    public static String freeText(String value, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive.");
        }
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new InvalidQueryParameterException("q is too long.");
        }
        return normalized;
    }

    public static <E extends Enum<E>> E enumValue(String value, Class<E> enumType, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidQueryParameterException(parameterName + " has an unsupported value.");
        }
    }
}
