package com.smartsoc.domain.common;

/**
 * Thrown when creating a resource whose natural key already exists
 * (e.g. an asset hostname). Translated to HTTP 409 Conflict by the API
 * layer — a uniqueness conflict, not a lifecycle rule violation (422).
 */
public class DuplicateResourceException extends DomainException {

    public DuplicateResourceException(String code, String message) {
        super(code, message);
    }
}
