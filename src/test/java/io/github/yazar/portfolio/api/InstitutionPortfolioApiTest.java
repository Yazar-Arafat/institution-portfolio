package io.github.yazar.portfolio.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.github.yazar.portfolio.europepmc.articles.ArticlesClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Runs the whole application against a local stand-in for Europe PMC (WireMock), so the tests exercise real
 * HTTP calls, timeouts and JSON handling without depending on the live service. The Articles API responses are
 * real ones recorded from Europe PMC and trimmed; see src/test/resources/fixtures.
 */
// The read timeout leaves room for a cold JVM's first responses; the slow stubs below take well over it.
@SpringBootTest(properties = {
        "europepmc.read-timeout=2s",
        "europepmc.lookup-timeout=5s"
})
@AutoConfigureMockMvc
class InstitutionPortfolioApiTest {

    private static final WireMockServer europePmc = new WireMockServer(wireMockConfig().dynamicPort());

    @Autowired
    private MockMvcTester mvc;

    @BeforeAll
    static void startEuropePmcStub() {
        europePmc.start();
    }

    @AfterAll
    static void stopEuropePmcStub() {
        europePmc.stop();
    }

    @DynamicPropertySource
    static void pointClientsAtStub(DynamicPropertyRegistry registry) {
        registry.add("europepmc.grist-base-url", () -> europePmc.baseUrl() + "/grist");
        registry.add("europepmc.articles-base-url", () -> europePmc.baseUrl() + "/articles");
    }

    @BeforeEach
    void resetStub() {
        europePmc.resetAll();
    }

    @Test
    void buildsAPortfolioWithAPublicationStatusForEveryGrant() {
        stubGrist(okJson(fixture("grist-cambridge.json")));
        stubArticles("217100/Z/19/Z", okJson(fixture("articles-wellcome-217100.json")));
        stubArticles("MR/P014178/1", okJson(fixture("articles-mrc-p014178.json")));
        stubArticles("12345", okJson(fixture("articles-colliding-12345.json")));
        stubArticles("FS/00/00000", okJson(fixture("articles-no-results.json")));

        MvcTestResult result = requestPortfolio("University of Cambridge");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.grants[*].grantId").asArray()
                .containsExactly("217100/Z/19/Z", "MR/P014178/1", "12345", "FS/00/00000");
        assertThat(result).bodyJson().extractingPath("$.grants[*].publications.status").asArray()
                .containsExactly("FOUND", "FOUND", "UNCERTAIN", "NONE_FOUND");
        assertThat(result).bodyJson().extractingPath("$.grants[2].publications.items[*].association").asArray()
                .containsOnly("UNCERTAIN");
        assertThat(result).bodyJson().extractingPath("$.summary.grantsByPublicationStatus.FOUND").isEqualTo(2);
        europePmc.verify(getRequestedFor(urlEqualTo(
                "/grist/rest/get/query=aff:%22University%20of%20Cambridge%22&resultType=core&format=json")));
    }

    @Test
    void reportsAFailedPublicationLookupOnItsGrantAndStillReturnsTheOthers() {
        stubGrist(okJson(fixture("grist-cambridge.json")));
        stubArticles("217100/Z/19/Z", okJson(fixture("articles-wellcome-217100.json")));
        stubArticles("MR/P014178/1", aResponse().withStatus(500));
        stubArticles("12345", okJson(fixture("articles-colliding-12345.json")));
        stubArticles("FS/00/00000", okJson(fixture("articles-no-results.json")));

        MvcTestResult result = requestPortfolio("University of Cambridge");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.grants[*].publications.status").asArray()
                .containsExactly("FOUND", "LOOKUP_FAILED", "UNCERTAIN", "NONE_FOUND");
        assertThat(result).bodyJson().extractingPath("$.grants[1].publications.error")
                .isEqualTo("Europe PMC Articles API returned HTTP 500");
    }

    @Test
    void returnsAnEmptyPortfolioWhenNoGrantsMatch() {
        stubGrist(okJson("{\"HitCount\": \"0\"}"));

        MvcTestResult result = requestPortfolio("Nowhere University");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.grants").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.summary.grantsReturned").isEqualTo(0);
    }

    @Test
    void returns502WhenTheGrantsApiFails() {
        stubGrist(aResponse().withStatus(503));

        MvcTestResult result = requestPortfolio("University of Cambridge");

        assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Europe PMC Grants API returned HTTP 503");
    }

    @Test
    void returns504WhenTheGrantsApiIsTooSlow() {
        stubGrist(okJson(fixture("grist-cambridge.json")).withFixedDelay(4_000));

        MvcTestResult result = requestPortfolio("University of Cambridge");

        assertThat(result).hasStatus(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(result).bodyJson().extractingPath("$.detail")
                .isEqualTo("Europe PMC Grants API did not respond in time");
    }

    @Test
    void returns504WhenTheGrantsApiStallsHalfwayThroughItsResponse() {
        // Headers arrive at once, but the body trickles in over 4 s. Spring reports this timeout as a broken
        // body rather than a timeout, so it must still be recognised as one.
        stubGrist(okJson(fixture("grist-cambridge.json")).withChunkedDribbleDelay(5, 4_000));

        MvcTestResult result = requestPortfolio("University of Cambridge");

        assertThat(result).hasStatus(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(result).bodyJson().extractingPath("$.detail")
                .isEqualTo("Europe PMC Grants API did not respond in time");
    }

    @Test
    void rejectsInvalidParametersWithoutCallingEuropePmc() {
        assertThat(mvc.get().uri("/institution-portfolio").param("institution", "Cambridge").param("limit", "11"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("limit");
        assertThat(mvc.get().uri("/institution-portfolio").param("institution", "Cambridge").param("publicationsPerGrant", "0"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("publicationsPerGrant");
        assertThat(mvc.get().uri("/institution-portfolio").param("institution", "&&&"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("letter or digit");
        assertThat(mvc.get().uri("/institution-portfolio"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        europePmc.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void publishesAnOpenApiDocumentThatSwaggerUiCanUse() {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.paths['/institution-portfolio'].get.parameters[*].name")
                .asArray().containsExactly("institution", "limit", "publicationsPerGrant");
        // Swagger UI validates "pattern" with JavaScript regexes, which cannot run Java-only syntax.
        assertThat(result).bodyJson().doesNotHavePath("$.paths['/institution-portfolio'].get.parameters[0].schema.pattern");
    }

    private MvcTestResult requestPortfolio(String institution) {
        return mvc.get().uri("/institution-portfolio")
                .param("institution", institution)
                .param("limit", "4")
                .exchange();
    }

    private static void stubGrist(ResponseDefinitionBuilder response) {
        europePmc.stubFor(get(urlPathMatching("/grist/rest/get/.*")).willReturn(response));
    }

    private static void stubArticles(String grantId, ResponseDefinitionBuilder response) {
        europePmc.stubFor(get(urlPathEqualTo("/articles/search"))
                .withQueryParam("query", equalTo(ArticlesClient.queryFor(grantId)))
                .willReturn(response));
    }

    private static String fixture(String name) {
        try {
            return new ClassPathResource("fixtures/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
