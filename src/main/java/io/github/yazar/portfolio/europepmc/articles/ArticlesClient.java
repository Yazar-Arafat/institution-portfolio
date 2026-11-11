package io.github.yazar.portfolio.europepmc.articles;

import io.github.yazar.portfolio.config.EuropePmcProperties;
import io.github.yazar.portfolio.europepmc.EuropePmcException;
import io.github.yazar.portfolio.europepmc.EuropePmcException.Failure;
import io.github.yazar.portfolio.europepmc.EuropePmcRequest;
import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Searches the Europe PMC Articles REST API for publications that cite a grant ID.
 *
 * @see <a href="https://dev.europepmc.org/RestfulWebService">Articles REST API documentation</a>
 */
@Component
public class ArticlesClient {

    public static final String API_NAME = "Europe PMC Articles API";

    private final RestClient restClient;
    private final URI baseUrl;
    private final Duration readTimeout;

    public ArticlesClient(RestClient europePmcRestClient, EuropePmcProperties properties) {
        this.restClient = europePmcRestClient;
        this.baseUrl = properties.articlesBaseUrl();
        this.readTimeout = properties.readTimeout();
    }

    /** Returns up to {@code maxResults} publications citing the grant ID, in the Articles API's order. */
    public ArticleSearchResult findByGrantId(String grantId, int maxResults) {
        String query = queryFor(grantId);
        ArticleSearchResponse response = EuropePmcRequest.send(API_NAME, readTimeout, () -> restClient.get()
                .uri(searchUri(baseUrl, query, maxResults))
                .retrieve()
                .body(ArticleSearchResponse.class));
        if (response == null) {
            throw new EuropePmcException(API_NAME, Failure.INVALID_RESPONSE, API_NAME + " returned an empty response", null);
        }
        return response.toResult(query);
    }

    /**
     * The grant ID is quoted so it is matched as a single value. Quotes and backslashes inside it are dropped so
     * it cannot end the phrase early: a malformed query is not an error here, it just silently matches nothing.
     * A bare ID also matches its longer cited form (see {@link GrantIds}), e.g.
     * {@code GRANT_ID:"217100" OR GRANT_ID:217100/*}; letters and digits need no escaping.
     */
    public static String queryFor(String grantId) {
        if (GrantIds.isBare(grantId)) {
            return "GRANT_ID:\"" + grantId + "\" OR GRANT_ID:" + grantId + "/*";
        }
        return "GRANT_ID:\"" + grantId.replaceAll("[\"\\\\]", " ").strip() + "\"";
    }

    /** {@code resultType=core} is needed because only core results include the grant list used to verify funders. */
    static URI searchUri(URI baseUrl, String query, int pageSize) {
        return UriComponentsBuilder.fromUri(baseUrl)
                .path("/search")
                .queryParam("query", "{query}")
                .queryParam("resultType", "core")
                .queryParam("format", "json")
                .queryParam("pageSize", "{pageSize}")
                .encode()
                .buildAndExpand(query, pageSize)
                .toUri();
    }
}
