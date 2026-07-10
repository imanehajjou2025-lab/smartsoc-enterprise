package com.smartsoc.domain.common;

/**
 * Thrown when a requested aggregate/entity does not exist (or is soft
 * deleted). Translated to HTTP 404 by the API layer.
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super("RESOURCE_NOT_FOUND",
                "%s with identifier '%s' was not found".formatted(resourceType, identifier));
    }
}
