package io.github.yazar.portfolio.europepmc.grist;

import io.github.yazar.portfolio.config.EuropePmcProperties;
import io.github.yazar.portfolio.europepmc.EuropePmcRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/**
 * Searches the Europe PMC Grants (Grist) API for awards held at an institution.
 *
 * @see <a href="https://dev.europepmc.org/GristAPI">Grist API documentation</a>
 */
@Component
public class GristClient {

    public static final String API_NAME = "Europe PMC Grants API";

    private final RestClient restClient;
    private final String baseUrl;
    private final Duration readTimeout;

    public GristClient(RestClient europePmcRestClient, EuropePmcProperties properties) {
        this.restClient = europePmcRestClient;
        this.baseUrl = StringUtils.trimTrailingCharacter(properties.gristBaseUrl().toString(), '/');
        this.readTimeout = properties.readTimeout();
    }

    /**
     * Returns the distinct grants on the first results page whose affiliation matches the name, in Grist's order.
     * A page holds 25 records, one per investigator, so it can hold far fewer than 25 grants.
     */
    public GrantSearchResult findGrantsByInstitution(String institutionName) {
        String query = InstitutionQuery.forName(institutionName);
        JsonNode body = EuropePmcRequest.send(API_NAME, readTimeout,
                () -> restClient.get().uri(searchUri(baseUrl, query)).retrieve().body(JsonNode.class));
        GrantSearchResult page = GristResponseParser.parse(query, body);
        List<Grant> grants = GrantRecords.mergeByGrantId(page.grants(), InstitutionQuery.phrase(institutionName));
        return new GrantSearchResult(query, page.totalHits(), grants);
    }

    /**
     * Grist takes its parameters in the URL path, not in a query string:
     * {@code /rest/get/query=aff:"..."&resultType=core&format=json}. The query is percent-encoded as a path
     * segment; {@code resultType=core} is needed because only core results include institution details.
     */
    static URI searchUri(String baseUrl, String query) {
        String encodedQuery = UriUtils.encodePathSegment(query, StandardCharsets.UTF_8);
        return URI.create(baseUrl + "/rest/get/query=" + encodedQuery + "&resultType=core&format=json");
    }
}
