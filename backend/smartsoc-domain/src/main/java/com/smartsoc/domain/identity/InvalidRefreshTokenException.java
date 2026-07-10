package com.smartsoc.domain.identity;

import com.smartsoc.domain.common.DomainException;

/**
 * Thrown when a refresh token is unknown, expired, revoked or reused.
 * Deliberately carries no detail about WHICH check failed: distinguishing
 * the cases would give an attacker an oracle. Translated to HTTP 401.
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", "The refresh token is invalid or has expired");
    }
}
