package io.github.yazar.portfolio.europepmc.grist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GrantRecordsTest {

    @Test
    void mergesTheRecordsOfEachInvestigatorIntoOneGrantInGristOrder() {
        List<Grant> records = List.of(
                record("352259", "University of Bristol"),
                record("352259", "Liverpool School of Tropical Medicine"),
                record("341725", "University of Manchester"),
                record("352259", "University of Manchester"));

        List<Grant> grants = GrantRecords.mergeByGrantId(records, "University of Manchester");

        assertThat(grants).extracting(Grant::id).containsExactly("352259", "341725");
        assertThat(grants.getFirst().institution().name()).isEqualTo("University of Manchester");
    }

    @Test
    void reportsTheFirstInstitutionWhenNoneHasTheSearchedName() {
        List<Grant> grants = GrantRecords.mergeByGrantId(List.of(
                record("100", "University of Sheffield"),
                record("100", "Monash University")), "University of Cambridge");

        assertThat(grants).singleElement()
                .satisfies(grant -> assertThat(grant.institution().name()).isEqualTo("University of Sheffield"));
    }

    @Test
    void matchesTheSearchedNameAsWholeWordsIgnoringCaseAndPunctuation() {
        List<Grant> grants = GrantRecords.mergeByGrantId(List.of(
                record("100", "Cambridgeshire County Council"),
                record("100", "University of Cambridge, Department of Medicine")), "cambridge");

        assertThat(grants.getFirst().institution().name()).isEqualTo("University of Cambridge, Department of Medicine");
    }

    @Test
    void keepsRecordsWithoutAnIdSeparate() {
        List<Grant> grants = GrantRecords.mergeByGrantId(List.of(
                record(null, "University of Manchester"),
                record(null, "University of Manchester")), "University of Manchester");

        assertThat(grants).hasSize(2);
    }

    private static Grant record(String id, String institution) {
        return new Grant(id, "Title", "Wellcome Trust", "Wellcome Trust",
                new Institution(institution, null, null), "2026-01-01", "2030-12-31");
    }
}
