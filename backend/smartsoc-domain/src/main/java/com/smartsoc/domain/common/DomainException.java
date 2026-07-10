package com.smartsoc.domain.common;

/**
 * Base class of every business exception thrown by the domain or the
 * application layer. Carries a stable machine-readable {@code code} used by
 * the API layer to build RFC 9457 problem responses, so clients can react
 * programmatically without parsing human-readable messages.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
