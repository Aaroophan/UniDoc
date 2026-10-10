package com.unidoc.common.web;

import com.unidoc.common.api.ApiErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonAccessDeniedHandler.class);

    private final ApiErrorWriter apiErrorWriter;

    public JsonAccessDeniedHandler(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        log.warn(
                "Access denied path={} requestId={}",
                request.getRequestURI(),
                RequestIdFilter.currentRequestId(request)
        );
        apiErrorWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action."
        );
    }
}
