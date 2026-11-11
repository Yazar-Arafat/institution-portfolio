package io.github.yazar.portfolio.europepmc;

import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.web.client.RestClientException;

/**
 * Sends one request to a Europe PMC API and turns any failure into an {@link EuropePmcException}, so every client
 * classifies failures the same way.
 */
public final class EuropePmcRequest {

    private EuropePmcRequest() {
    }

    public static <T> T send(String api, Duration readTimeout, Supplier<T> request) {
        long started = System.nanoTime();
        try {
            return request.get();
        } catch (RestClientException e) {
            // When the read timeout fires while the body is still arriving, Spring reports a broken body rather
            // than a timeout, so any failure after the read timeout has elapsed counts as a timeout.
            boolean ranPastReadTimeout = System.nanoTime() - started >= readTimeout.toNanos();
            throw EuropePmcException.from(api, e, ranPastReadTimeout);
        }
    }
}
