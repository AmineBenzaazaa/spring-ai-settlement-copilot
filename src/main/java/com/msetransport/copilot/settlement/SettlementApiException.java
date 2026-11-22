package com.msetransport.copilot.settlement;

/** Base type for failures talking to the settlement platform. Messages never contain tokens. */
public abstract sealed class SettlementApiException extends RuntimeException
        permits SettlementApiException.NotFound, SettlementApiException.AccessDenied,
        SettlementApiException.Unavailable, SettlementApiException.BadRequest {

    protected SettlementApiException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 404: the row does not exist <em>for this caller</em> (the platform hides other tenants' rows). */
    public static final class NotFound extends SettlementApiException {
        public NotFound(String message) {
            super(message, null);
        }
    }

    /** 401/403: the token expired or the role may not do this. Never retried. */
    public static final class AccessDenied extends SettlementApiException {
        public AccessDenied(String message) {
            super(message, null);
        }
    }

    /** 400: the copilot sent a filter the platform rejected. Never retried. */
    public static final class BadRequest extends SettlementApiException {
        public BadRequest(String message) {
            super(message, null);
        }
    }

    /** 5xx, timeout, connection refused or open circuit: retried, then reported as unavailable. */
    public static final class Unavailable extends SettlementApiException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
