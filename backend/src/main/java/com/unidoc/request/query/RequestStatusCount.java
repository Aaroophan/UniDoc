package com.unidoc.request.query;

import com.unidoc.request.RequestStatus;

public record RequestStatusCount(RequestStatus status, long count) {
}
