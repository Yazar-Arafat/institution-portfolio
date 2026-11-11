package io.github.yazar.portfolio.europepmc.articles;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ArticlesClientTest {

    @Test
    void quotesTheGrantIdAsOneValue() {
        assertThat(ArticlesClient.queryFor("MR/P014178/1")).isEqualTo("GRANT_ID:\"MR/P014178/1\"");
    }

    @Test
    void alsoSearchesTheLongerCitedFormOfABareId() {
        assertThat(ArticlesClient.queryFor("217100")).isEqualTo("GRANT_ID:\"217100\" OR GRANT_ID:217100/*");
    }

    @Test
    void treatsOnlyLettersAndDigitsWithAtLeastOneDigitAsBare() {
        assertThat(GrantIds.isBare("217100")).isTrue();
        assertThat(GrantIds.isBare("MC_U117532067")).isFalse();
        assertThat(GrantIds.isBare("FS/00/00000")).isFalse();
        assertThat(GrantIds.isBare("OR")).isFalse();
    }

    @Test
    void dropsCharactersThatWouldEndTheQuotedIdEarly() {
        assertThat(ArticlesClient.queryFor("12\"34\\")).isEqualTo("GRANT_ID:\"12 34\"");
    }

    @Test
    void requestsCoreResultsAndEncodesTheQuery() {
        URI uri = ArticlesClient.searchUri(URI.create("https://example.org/rest"), "GRANT_ID:\"MR/P014178/1\"", 3);

        assertThat(uri).hasToString("https://example.org/rest/search"
                + "?query=GRANT_ID%3A%22MR%2FP014178%2F1%22&resultType=core&format=json&pageSize=3");
    }
}
