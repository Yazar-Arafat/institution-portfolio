package io.github.yazar.portfolio.europepmc;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * A call to a Europe PMC API failed. {@link #failure()} says how, so callers can respond appropriately,
 * for example with 504 for a timeout and 502 otherwise.
 */
public class EuropePmcException extends RuntimeException {

    public enum Failure {
        /** No response within the configured timeouts. */
        TIMEOUT,
        /** The API could not be reached or answered with an error status. */
        UNAVAILABLE,
        /** The API answered, but not with something this service could read. */
        INVALID_RESPONSE
    }

    private static final long serialVersionUID = 1L;

    private final String api;
    private final Failure failure;

    public EuropePmcException(String api, Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.api = api;
        this.failure = failure;
    }

    /**
     * Translates a Spring HTTP client exception into a description of what went wrong upstream.
     *
     * @param ranPastReadTimeout whether the call failed only after the read timeout had elapsed. When the timeout
     *                           fires while the body is still arriving, Spring reports a broken body rather than
     *                           a timeout, so elapsed time is the reliable signal.
     */
    static EuropePmcException from(String api, RestClientException exception, boolean ranPastReadTimeout) {
        if (exception instanceof RestClientResponseException response) {
            return new EuropePmcException(api, Failure.UNAVAILABLE,
                    api + " returned HTTP " + response.getStatusCode().value(), exception);
        }
        if (ranPastReadTimeout || isCausedByTimeout(exception)) {
            return new EuropePmcException(api, Failure.TIMEOUT, api + " did not respond in time", exception);
        }
        if (exception instanceof ResourceAccessException) {
            return new EuropePmcException(api, Failure.UNAVAILABLE, api + " could not be reached", exception);
        }
        return new EuropePmcException(api, Failure.INVALID_RESPONSE,
                api + " returned a response that could not be read", exception);
    }

    private static boolean isCausedByTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    public String api() {
        return api;
    }

    public Failure failure() {
        return failure;
    }
}
