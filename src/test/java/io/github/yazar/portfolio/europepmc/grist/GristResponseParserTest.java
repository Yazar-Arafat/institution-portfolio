package io.github.yazar.portfolio.europepmc.grist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yazar.portfolio.europepmc.EuropePmcException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GristResponseParserTest {

    private static final String QUERY = "aff:\"University of Cambridge\"";
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsGrantsInGristOrder() throws IOException {
        GrantSearchResult result = parse(new ClassPathResource("fixtures/grist-cambridge.json")
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(result.totalHits()).isEqualTo(4);
        assertThat(result.grants()).extracting(Grant::id)
                .containsExactly("217100/Z/19/Z", "MR/P014178/1", "12345", "FS/00/00000");

        Grant mrcGrant = result.grants().get(1);
        assertThat(mrcGrant.funder()).isEqualTo("Medical Research Council");
        assertThat(mrcGrant.funderSearchTerm()).isEqualTo("Medical Research Council");
        assertThat(mrcGrant.institution())
                .isEqualTo(new Institution("University of Cambridge", "Department of Medicine", "ror.org/013meh722"));
        assertThat(mrcGrant.startDate()).isEqualTo("2017-04-01");
    }

    @Test
    void acceptsASingleRecordGivenAsAnObjectInsteadOfAnArray() {
        GrantSearchResult result = parse("""
                {"HitCount": "1", "RecordList": {"Record":
                    {"Grant": {"Id": "081052", "Funder": {"Name": "Wellcome Trust"}},
                     "Institution": {"Name": "University College London"}}}}
                """);

        assertThat(result.grants()).singleElement().satisfies(grant -> {
            assertThat(grant.id()).isEqualTo("081052");
            assertThat(grant.institution().name()).isEqualTo("University College London");
        });
    }

    @Test
    void usesTheFirstFunderAndInstitutionWhenSeveralAreListed() {
        GrantSearchResult result = parse("""
                {"HitCount": "1", "RecordList": {"Record":
                    {"Grant": {"Id": "081052",
                               "Funder": [{"Name": "Wellcome Trust"}, {"Name": "Medical Research Council"}]},
                     "Institution": [{"Name": "University College London", "RORID": "ror.org/02jx3x895"},
                                     {"Name": "University of Cambridge"}]}}}
                """);

        Grant grant = result.grants().getFirst();
        assertThat(grant.funder()).isEqualTo("Wellcome Trust");
        assertThat(grant.institution())
                .isEqualTo(new Institution("University College London", null, "ror.org/02jx3x895"));
    }

    @Test
    void treatsAMissingOrEmptyRecordListAsNoGrants() {
        assertThat(parse("{\"HitCount\": \"0\"}").grants()).isEmpty();
        assertThat(parse("{\"HitCount\": \"0\", \"RecordList\": \"\"}").grants()).isEmpty();
    }

    @Test
    void toleratesMissingFieldsAndTheOlderPlainStringFunder() {
        GrantSearchResult result = parse("""
                {"RecordList": {"Record": [{"Grant": {"Id": "107740", "Funder": "Wellcome Trust", "Title": "  "}}]}}
                """);

        Grant grant = result.grants().getFirst();
        assertThat(result.totalHits()).isNull();
        assertThat(grant.funder()).isEqualTo("Wellcome Trust");
        assertThat(grant.title()).isNull();
        assertThat(grant.institution()).isEqualTo(new Institution(null, null, null));
    }

    @Test
    void rejectsAResponseThatIsNotAJsonObject() {
        assertThatThrownBy(() -> parse("[]"))
                .isInstanceOfSatisfying(EuropePmcException.class, e ->
                        assertThat(e.failure()).isEqualTo(EuropePmcException.Failure.INVALID_RESPONSE));
    }

    private GrantSearchResult parse(String body) {
        JsonNode root = json.readTree(body);
        return GristResponseParser.parse(QUERY, root);
    }
}
