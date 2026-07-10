package com.smartsoc.domain.common;

/**
 * Thrown when an operation is syntactically valid but violates a business
 * rule (e.g. closing an incident that still has open tasks). Translated to
 * HTTP 422 by the API layer.
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }
}
