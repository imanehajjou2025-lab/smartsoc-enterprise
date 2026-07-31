package com.smartsoc.api.common.error;

import com.smartsoc.application.ai.AiServiceUnavailableException;
import com.smartsoc.application.settings.DatabaseBackupPort.BackupExecutionException;
import com.smartsoc.application.settings.NotificationTestPort.NotificationTestException;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.DomainException;
import com.smartsoc.domain.common.DuplicateResourceException;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.identity.InvalidRefreshTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Single place where every error crossing the API boundary is translated
 * into an RFC 9457 (Problem Details) response. Guarantees:
 * - a uniform, machine-readable error contract for the frontend;
 * - no stacktrace or internal detail ever leaks to clients;
 * - unexpected errors are logged server-side with full context.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROPERTY_CODE = "code";
    private static final String PROPERTY_TIMESTAMP = "timestamp";
    private static final String PROPERTY_ERRORS = "errors";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return problemOf(HttpStatus.NOT_FOUND, "Resource not found", ex);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return problemOf(HttpStatus.UNAUTHORIZED, "Invalid refresh token", ex);
    }

    /**
     * Method-security denials (@PreAuthorize) surface inside the controller
     * call, so they reach this advice instead of the filter-chain handler —
     * without this mapping the generic handler would turn them into 500s.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
        problem.setTitle("Access denied");
        problem.setProperty(PROPERTY_CODE, "ACCESS_DENIED");
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        return problem;
    }

    /** Bad credentials, disabled account… — always the same opaque 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationFailure(AuthenticationException ex) {
        log.debug("Authentication failure: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid username or password");
        problem.setTitle("Authentication failed");
        problem.setProperty(PROPERTY_CODE, "AUTHENTICATION_FAILED");
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        return problem;
    }

    /** Classifieur IA injoignable sur une demande explicite (ADR-008). */
    @ExceptionHandler(AiServiceUnavailableException.class)
    public ProblemDetail handleAiServiceUnavailable(AiServiceUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("AI service unavailable");
        problem.setProperty(PROPERTY_CODE, ex.getCode());
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        return problem;
    }

    /** L'outil pg_dump a échoué ou n'a pas répondu (console Paramètres). */
    @ExceptionHandler(BackupExecutionException.class)
    public ProblemDetail handleBackupExecutionFailure(BackupExecutionException ex) {
        log.warn("Database backup failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Backup failed");
        problem.setProperty(PROPERTY_CODE, "BACKUP_EXECUTION_FAILED");
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        return problem;
    }

    /** Le SMTP réel n'a pas pu envoyer l'e-mail de test (console Paramètres). */
    @ExceptionHandler(NotificationTestException.class)
    public ProblemDetail handleNotificationTestFailure(NotificationTestException ex) {
        log.warn("Notification test failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Notification test failed");
        problem.setProperty(PROPERTY_CODE, "NOTIFICATION_TEST_FAILED");
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        return problem;
    }

    /** Conflit d'unicité (ex. hostname d'actif déjà inventorié) → 409. */
    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(DuplicateResourceException ex) {
        return problemOf(HttpStatus.CONFLICT, "Resource conflict", ex);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException ex) {
        return problemOf(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation", ex);
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex) {
        return problemOf(HttpStatus.BAD_REQUEST, "Invalid request", ex);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error while processing request", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support if the problem persists.");
        problem.setTitle("Internal server error");
        problem.setProperty(PROPERTY_CODE, "INTERNAL_ERROR");
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        return problem;
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() == null
                                ? "invalid value" : fieldError.getDefaultMessage(),
                        (first, second) -> first));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation error");
        problem.setProperty(PROPERTY_CODE, "VALIDATION_FAILED");
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        problem.setProperty(PROPERTY_ERRORS, fieldErrors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    private ProblemDetail problemOf(HttpStatus status, String title, DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(title);
        problem.setProperty(PROPERTY_CODE, ex.getCode());
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        return problem;
    }
}
