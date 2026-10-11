package com.unidoc.common.exception;

import com.unidoc.auth.InactiveAccountException;
import com.unidoc.auth.InvalidCredentialsException;
import com.unidoc.auth.SessionRevokedException;
import com.unidoc.catalogue.DocumentTypeInactiveException;
import com.unidoc.catalogue.DocumentTypeNotFoundException;
import com.unidoc.catalogue.DuplicateDocumentTypeCodeException;
import com.unidoc.catalogue.DuplicateDocumentTypeNameException;
import com.unidoc.common.api.ApiErrorCode;
import com.unidoc.common.api.ApiErrorResponse;
import com.unidoc.common.web.RequestIdFilter;
import com.unidoc.common.web.SafeStackTrace;
import com.unidoc.file.AttachmentNotFoundException;
import com.unidoc.file.FileStorageException;
import com.unidoc.file.FileValidationException;
import com.unidoc.profile.DuplicateRegistrationNumberException;
import com.unidoc.profile.ProfileIncompleteException;
import com.unidoc.request.DeliveryMethodMismatchException;
import com.unidoc.request.DocumentNotReadyException;
import com.unidoc.request.DocumentRequestNotFoundException;
import com.unidoc.request.InvalidRequestTransitionException;
import com.unidoc.request.IssuedDocumentAlreadyExistsException;
import com.unidoc.request.IssuedDocumentNotFoundException;
import com.unidoc.request.RequestVersionConflictException;
import com.unidoc.user.DuplicateEmailException;
import com.unidoc.user.SelfDeactivationNotAllowedException;
import com.unidoc.user.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<String, ConstraintMapping> CONSTRAINT_MAPPINGS = Map.of(
            "uq_users_email", new ConstraintMapping(
                    ApiErrorCode.EMAIL_ALREADY_EXISTS,
                    "An account already uses this email address."
            ),
            "uq_student_profiles_registration", new ConstraintMapping(
                    ApiErrorCode.REGISTRATION_NUMBER_ALREADY_EXISTS,
                    "This registration number is already in use."
            ),
            "uq_document_types_code", new ConstraintMapping(
                    ApiErrorCode.DOCUMENT_TYPE_CODE_EXISTS,
                    "A document type already uses this code."
            ),
            "uq_document_types_name", new ConstraintMapping(
                    ApiErrorCode.DOCUMENT_TYPE_NAME_EXISTS,
                    "A document type already uses this name."
            ),
            "uq_issued_documents_request", new ConstraintMapping(
                    ApiErrorCode.ISSUED_DOCUMENT_ALREADY_EXISTS,
                    "An issued document already exists for this request."
            )
    );

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTH_INVALID_CREDENTIALS, exception.getMessage(), request);
    }

    @ExceptionHandler(SessionRevokedException.class)
    ResponseEntity<ApiErrorResponse> handleSessionRevoked(
            SessionRevokedException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.UNAUTHORIZED, ApiErrorCode.SESSION_REVOKED, exception.getMessage(), request);
    }

    @ExceptionHandler(InactiveAccountException.class)
    ResponseEntity<ApiErrorResponse> handleInactiveAccount(
            InactiveAccountException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_INACTIVE, exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return expected(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action.",
                request
        );
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiErrorResponse> handleUnmappedResource(Exception exception, HttpServletRequest request) {
        return expected(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found.",
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedHttpMethod(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        ResponseEntity<ApiErrorResponse> result = expected(
                HttpStatus.METHOD_NOT_ALLOWED,
                ApiErrorCode.INVALID_REQUEST,
                "This HTTP method is not supported for the requested endpoint.",
                request
        );
        Set<HttpMethod> allowedMethods = exception.getSupportedHttpMethods();
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            return result;
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(allowedMethods.toArray(new HttpMethod[0]))
                .body(result.getBody());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateEmail(DuplicateEmailException exception, HttpServletRequest request) {
        return expected(HttpStatus.CONFLICT, ApiErrorCode.EMAIL_ALREADY_EXISTS, exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateRegistrationNumberException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateRegistration(
            DuplicateRegistrationNumberException exception,
            HttpServletRequest request
    ) {
        return expected(
                HttpStatus.CONFLICT,
                ApiErrorCode.REGISTRATION_NUMBER_ALREADY_EXISTS,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(SelfDeactivationNotAllowedException.class)
    ResponseEntity<ApiErrorResponse> handleSelfDeactivation(
            SelfDeactivationNotAllowedException exception,
            HttpServletRequest request
    ) {
        return expected(
                HttpStatus.CONFLICT,
                ApiErrorCode.SELF_DEACTIVATION_NOT_ALLOWED,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleUserNotFound(UserNotFoundException exception, HttpServletRequest request) {
        return expected(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentTypeNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleDocumentTypeNotFound(
            DocumentTypeNotFoundException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.NOT_FOUND, ApiErrorCode.DOCUMENT_TYPE_NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentTypeInactiveException.class)
    ResponseEntity<ApiErrorResponse> handleDocumentTypeInactive(
            DocumentTypeInactiveException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.CONFLICT, ApiErrorCode.DOCUMENT_TYPE_INACTIVE, exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateDocumentTypeCodeException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateDocumentTypeCode(
            DuplicateDocumentTypeCodeException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.CONFLICT, ApiErrorCode.DOCUMENT_TYPE_CODE_EXISTS, exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateDocumentTypeNameException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateDocumentTypeName(
            DuplicateDocumentTypeNameException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.CONFLICT, ApiErrorCode.DOCUMENT_TYPE_NAME_EXISTS, exception.getMessage(), request);
    }

    @ExceptionHandler(ProfileIncompleteException.class)
    ResponseEntity<ApiErrorResponse> handleProfileIncomplete(
            ProfileIncompleteException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.BAD_REQUEST, ApiErrorCode.PROFILE_INCOMPLETE, exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentRequestNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleRequestNotFound(
            DocumentRequestNotFoundException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.NOT_FOUND, ApiErrorCode.REQUEST_NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestTransitionException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidTransition(
            InvalidRequestTransitionException exception,
            HttpServletRequest request
    ) {
        return expected(
                HttpStatus.CONFLICT,
                ApiErrorCode.INVALID_REQUEST_TRANSITION,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DeliveryMethodMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleDeliveryMismatch(
            DeliveryMethodMismatchException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.CONFLICT, ApiErrorCode.DELIVERY_METHOD_MISMATCH, exception.getMessage(), request);
    }

    @ExceptionHandler(RequestVersionConflictException.class)
    ResponseEntity<ApiErrorResponse> handleVersionConflict(
            RequestVersionConflictException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.CONFLICT, ApiErrorCode.REQUEST_VERSION_CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(IssuedDocumentNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleIssuedDocumentNotFound(
            IssuedDocumentNotFoundException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.NOT_FOUND, ApiErrorCode.ISSUED_DOCUMENT_NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(IssuedDocumentAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleIssuedDocumentAlreadyExists(
            IssuedDocumentAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        return expected(
                HttpStatus.CONFLICT,
                ApiErrorCode.ISSUED_DOCUMENT_ALREADY_EXISTS,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DocumentNotReadyException.class)
    ResponseEntity<ApiErrorResponse> handleDocumentNotReady(
            DocumentNotReadyException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.CONFLICT, ApiErrorCode.DOCUMENT_NOT_READY, exception.getMessage(), request);
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleAttachmentNotFound(
            AttachmentNotFoundException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.NOT_FOUND, ApiErrorCode.ATTACHMENT_NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(FileValidationException.class)
    ResponseEntity<ApiErrorResponse> handleFileValidation(
            FileValidationException exception,
            HttpServletRequest request
    ) {
        return expected(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(FileStorageException.class)
    ResponseEntity<ApiErrorResponse> handleFileStorage(FileStorageException exception, HttpServletRequest request) {
        String requestId = RequestIdFilter.currentRequestId(request);
        log.error(
                "File storage operation failed path={} requestId={} type={}{}",
                request.getRequestURI(),
                requestId,
                exception.getClass().getSimpleName(),
                SafeStackTrace.render(exception)
        );
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.FILE_STORAGE_ERROR,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        LinkedHashMap<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(error -> error.getField() + ":" + safeText(error.getDefaultMessage())))
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), safeText(error.getDefaultMessage())));

        return expected(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR,
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request
    ) {
        return expected(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                "Request validation failed.", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ConstraintViolation<?>> violations = new ArrayList<>(exception.getConstraintViolations());
        violations.sort(Comparator.comparing(violation -> violation.getPropertyPath().toString()));

        LinkedHashMap<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : violations) {
            String field = leafProperty(violation.getPropertyPath().toString());
            fieldErrors.putIfAbsent(field, safeText(violation.getMessage()));
        }

        return expected(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR,
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler({
            InvalidQueryParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidParameter(Exception exception, HttpServletRequest request) {
        String message = exception instanceof InvalidQueryParameterException
                ? exception.getMessage()
                : "A request parameter is missing or invalid.";
        return expected(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PARAMETER, message, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleMultipartTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return expected(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.FILE_TOO_LARGE,
                "One or more files exceed the allowed size.",
                request
        );
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MultipartException.class})
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return expected(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.MALFORMED_REQUEST,
                "Request body is invalid or malformed.",
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        ConstraintMapping mapping = knownConstraint(exception);
        if (mapping != null) {
            return expected(HttpStatus.CONFLICT, mapping.code(), mapping.message(), request);
        }
        return expected(
                HttpStatus.CONFLICT,
                ApiErrorCode.CONFLICT,
                "The request conflicts with existing data.",
                request
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return expected(
                HttpStatus.CONFLICT,
                ApiErrorCode.REQUEST_VERSION_CONFLICT,
                "The request changed while you were viewing it. Reload and try again.",
                request
        );
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ApiErrorResponse> handleDataUnavailable(
            DataAccessResourceFailureException exception,
            HttpServletRequest request
    ) {
        String requestId = RequestIdFilter.currentRequestId(request);
        log.error(
                "Database resource unavailable path={} requestId={} type={}{}",
                request.getRequestURI(),
                requestId,
                exception.getClass().getSimpleName(),
                SafeStackTrace.render(exception)
        );
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable. Try again later.",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = RequestIdFilter.currentRequestId(request);
        log.error(
                "Unhandled request failure path={} requestId={} type={}{}",
                request.getRequestURI(),
                requestId,
                exception.getClass().getName(),
                SafeStackTrace.render(exception)
        );
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Use the request reference if you need support.",
                request,
                null
        );
    }

    private ResponseEntity<ApiErrorResponse> expected(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        return expected(status, code, message, request, null);
    }

    private ResponseEntity<ApiErrorResponse> expected(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        if (status.is4xxClientError()) {
            log.info(
                    "API request rejected status={} code={} path={} requestId={}",
                    status.value(),
                    code,
                    request.getRequestURI(),
                    RequestIdFilter.currentRequestId(request)
            );
        }
        return response(status, code, message, request, fieldErrors);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
                status,
                code,
                message,
                request.getRequestURI(),
                RequestIdFilter.currentRequestId(request),
                fieldErrors
        ));
    }

    private static ConstraintMapping knownConstraint(Throwable throwable) {
        String explicitName = explicitConstraintName(throwable);
        if (explicitName != null) {
            ConstraintMapping mapping = CONSTRAINT_MAPPINGS.get(explicitName.toLowerCase(Locale.ROOT));
            if (mapping != null) {
                return mapping;
            }
        }

        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                for (Map.Entry<String, ConstraintMapping> entry : CONSTRAINT_MAPPINGS.entrySet()) {
                    if (lower.contains(entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private static String explicitConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private static String leafProperty(String propertyPath) {
        int index = propertyPath.lastIndexOf('.');
        return index >= 0 ? propertyPath.substring(index + 1) : propertyPath;
    }

    private static String safeText(String message) {
        return message == null || message.isBlank() ? "is invalid" : message;
    }

    private record ConstraintMapping(ApiErrorCode code, String message) {
    }
}
