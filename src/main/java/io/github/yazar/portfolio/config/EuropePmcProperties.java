package io.github.yazar.portfolio.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for calling Europe PMC, bound from {@code europepmc.*} in application.properties.
 *
 * @param gristBaseUrl         base URL of the Grants (Grist) API
 * @param articlesBaseUrl      base URL of the Articles REST API
 * @param connectTimeout       how long to wait to open a connection to Europe PMC
 * @param readTimeout          how long to wait for a response once a request has been sent
 * @param maxConcurrentLookups most Articles API requests in flight at once, shared by all incoming requests
 * @param lookupTimeout        deadline for one grant's publication lookup, including time queued for a free slot
 */
@Validated
@ConfigurationProperties("europepmc")
public record EuropePmcProperties(
        @NotNull URI gristBaseUrl,
        @NotNull URI articlesBaseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Positive int maxConcurrentLookups,
        @NotNull Duration lookupTimeout) {
}
