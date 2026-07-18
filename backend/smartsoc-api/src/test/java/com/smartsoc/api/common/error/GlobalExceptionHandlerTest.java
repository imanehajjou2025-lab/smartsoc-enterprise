package com.smartsoc.api.common.error;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.DuplicateResourceException;
import com.smartsoc.domain.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void resourceNotFoundIsTranslatedTo404WithStableCode() {
        ProblemDetail problem = handler.handleResourceNotFound(
                new ResourceNotFoundException("Alert", "42"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).contains("Alert").contains("42");
        assertThat(problem.getProperties()).containsEntry("code", "RESOURCE_NOT_FOUND");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void duplicateResourceIsTranslatedTo409WithStableCode() {
        // Contrat du frontend : le code ASSET_ALREADY_EXISTS dans le
        // ProblemDetail permet d'afficher « ce hostname existe déjà ».
        ProblemDetail problem = handler.handleDuplicateResource(
                new DuplicateResourceException("ASSET_ALREADY_EXISTS",
                        "An asset already exists for hostname 'srv-web-01'"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Resource conflict");
        assertThat(problem.getDetail()).contains("srv-web-01");
        assertThat(problem.getProperties()).containsEntry("code", "ASSET_ALREADY_EXISTS");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void businessRuleViolationIsTranslatedTo422() {
        ProblemDetail problem = handler.handleBusinessRuleViolation(
                new BusinessRuleViolationException("Incident still has open tasks"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getProperties()).containsEntry("code", "BUSINESS_RULE_VIOLATION");
    }

    @Test
    void unexpectedExceptionNeverLeaksInternalDetails() {
        ProblemDetail problem = handler.handleUnexpected(
                new IllegalStateException("secret internal detail: db password"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("secret internal detail");
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }
}
