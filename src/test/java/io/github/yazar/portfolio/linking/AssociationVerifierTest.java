package io.github.yazar.portfolio.linking;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yazar.portfolio.europepmc.articles.Article;
import io.github.yazar.portfolio.europepmc.articles.FundingReference;
import io.github.yazar.portfolio.europepmc.grist.Grant;
import io.github.yazar.portfolio.linking.AssociationVerifier.Assessment;
import io.github.yazar.portfolio.model.Association;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssociationVerifierTest {

    private static final Grant MRC_GRANT = grant("MR/P014178/1", "Medical Research Council");
    private static final Grant WELLCOME_GRANT = grant("217100/Z/19/Z", "Wellcome Trust");

    @Test
    void verifiesWhenTheArticleListsTheSameGrantIdAndFunder() {
        Assessment assessment = AssociationVerifier.assess(MRC_GRANT,
                article(new FundingReference("MR/P014178/1", "Medical Research Council")));

        assertThat(assessment.association()).isEqualTo(Association.VERIFIED);
        assertThat(assessment.note()).isNull();
    }

    @Test
    void verifiesDespiteFunderNameVariants() {
        assertThat(AssociationVerifier.assess(MRC_GRANT,
                article(new FundingReference("MR/P014178/1", "UKRI | Medical Research Council"))).association())
                .isEqualTo(Association.VERIFIED);
        assertThat(AssociationVerifier.assess(WELLCOME_GRANT,
                article(new FundingReference("217100/z/19/z", "Wellcome"))).association())
                .isEqualTo(Association.VERIFIED);
    }

    @Test
    void verifiesACouncilGrantThatTheArticleCreditsToUkri() {
        Grant mrcGrant = grant("MR/T030062/1", "Medical Research Council");

        for (String agency : List.of("UKRI", "UK Research and Innovation", "UKRI and Wellcome Trust")) {
            assertThat(AssociationVerifier.assess(mrcGrant, article(new FundingReference("MR/T030062/1", agency)))
                    .association()).as(agency).isEqualTo(Association.VERIFIED);
        }
    }

    @Test
    void doesNotTreatUkriAsTheFunderOfAGrantFromOutsideUkri() {
        Assessment assessment = AssociationVerifier.assess(grant("12345", "Wellcome Trust"),
                article(new FundingReference("12345", "UKRI")));

        assertThat(assessment.association()).isEqualTo(Association.UNCERTAIN);
    }

    @Test
    void doesNotLetUkriStandInForADifferentGrantId() {
        Assessment assessment = AssociationVerifier.assess(grant("MR/T030062/1", "Medical Research Council"),
                article(new FundingReference("MR/X000000/1", "UKRI")));

        assertThat(assessment.association()).isEqualTo(Association.UNCERTAIN);
        assertThat(assessment.note()).contains("does not list grant ID MR/T030062/1");
    }

    @Test
    void recognisesUkriOnlyAsAWholeWord() {
        Assessment assessment = AssociationVerifier.assess(grant("MR/T030062/1", "Medical Research Council"),
                article(new FundingReference("MR/T030062/1", "Ukrinform Media Fund")));

        assertThat(assessment.association()).isEqualTo(Association.UNCERTAIN);
    }

    @Test
    void isUncertainWhenTheSameIdBelongsToAnotherFunder() {
        Grant grant = grant("12345", "Wellcome Trust");

        Assessment assessment = AssociationVerifier.assess(grant,
                article(new FundingReference("12345", "Gates Foundation")));

        assertThat(assessment.association()).isEqualTo(Association.UNCERTAIN);
        assertThat(assessment.note()).contains("Gates Foundation", "Wellcome Trust");
    }

    @Test
    void verifiesTheLongerCitedFormOfABareGrantId() {
        Assessment assessment = AssociationVerifier.assess(grant("217100", "Wellcome Trust"),
                article(new FundingReference("217100/Z/19/Z", "Wellcome Trust")));

        assertThat(assessment.association()).isEqualTo(Association.VERIFIED);
    }

    @Test
    void onlyBareIdsMatchALongerForm() {
        Assessment longerThanAnMrcId = AssociationVerifier.assess(grant("MR/P014178", "Medical Research Council"),
                article(new FundingReference("MR/P014178/1", "Medical Research Council")));
        Assessment shorterThanTheGrantId = AssociationVerifier.assess(WELLCOME_GRANT,
                article(new FundingReference("217100", "Wellcome Trust")));

        assertThat(longerThanAnMrcId.association()).isEqualTo(Association.UNCERTAIN);
        assertThat(shorterThanTheGrantId.association()).isEqualTo(Association.UNCERTAIN);
        assertThat(shorterThanTheGrantId.note()).contains("grant ID 217100/Z/19/Z");
    }

    @Test
    void isUncertainWhenTheArticleDoesNotNameTheFunder() {
        Assessment assessment = AssociationVerifier.assess(MRC_GRANT,
                article(new FundingReference("MR/P014178/1", null)));

        assertThat(assessment.association()).isEqualTo(Association.UNCERTAIN);
        assertThat(assessment.note()).contains("does not name its funder");
    }

    @Test
    void isUncertainWhenTheGrantHasNoFunder() {
        Assessment assessment = AssociationVerifier.assess(grant("MR/P014178/1", null),
                article(new FundingReference("MR/P014178/1", "Medical Research Council")));

        assertThat(assessment.association()).isEqualTo(Association.UNCERTAIN);
    }

    private static Grant grant(String id, String funder) {
        return new Grant(id, "A grant", funder, funder, null, null, null);
    }

    private static Article article(FundingReference... funding) {
        return new Article("MED", "1", null, "1", null, "An article", "A journal", 2024, List.of(funding));
    }
}
