package io.github.yazar.portfolio.europepmc.grist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class InstitutionQueryTest {

    @Test
    void sendsTheWholeNameAsOneQuotedPhrase() {
        assertThat(InstitutionQuery.forName("University of Cambridge")).isEqualTo("aff:\"University of Cambridge\"");
    }

    @Test
    void collapsesWhitespace() {
        assertThat(InstitutionQuery.forName("  University   of\tManchester ")).isEqualTo("aff:\"University of Manchester\"");
    }

    @Test
    void keepsApostrophesAndAccentedLetters() {
        assertThat(InstitutionQuery.forName("King's College London")).isEqualTo("aff:\"King's College London\"");
        assertThat(InstitutionQuery.forName("Universität Zürich")).isEqualTo("aff:\"Universität Zürich\"");
    }

    @Test
    void userInputCannotEndThePhraseAndAddSearchTerms() {
        assertThat(InstitutionQuery.forName("Cambridge\" OR ga:\"Wellcome"))
                .isEqualTo("aff:\"Cambridge OR ga: Wellcome\"");
    }

    @Test
    void replacesCharactersThatWouldBreakTheUrlPath() {
        assertThat(InstitutionQuery.forName("Texas A&M; x=y/z?#%\\")).isEqualTo("aff:\"Texas A M x y z\"");
    }

    @Test
    void rejectsNamesWithNothingLeftToSearch() {
        assertThatIllegalArgumentException().isThrownBy(() -> InstitutionQuery.forName("\"&/ "));
    }

    @Test
    void encodesTheQueryIntoTheGristUrlPath() {
        String query = InstitutionQuery.forName("Universität of Cambridge");

        assertThat(GristClient.searchUri("https://example.org/GristAPI", query)).hasToString(
                "https://example.org/GristAPI/rest/get/query=aff:%22Universit%C3%A4t%20of%20Cambridge%22"
                        + "&resultType=core&format=json");
    }
}
