package io.github.yazar.portfolio.linking;

import io.github.yazar.portfolio.europepmc.articles.Article;
import io.github.yazar.portfolio.europepmc.articles.FundingReference;
import io.github.yazar.portfolio.europepmc.articles.GrantIds;
import io.github.yazar.portfolio.europepmc.grist.Grant;
import io.github.yazar.portfolio.model.Association;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Decides whether a publication found by a GRANT_ID search really belongs to the grant.
 *
 * <p>The Articles API matches on the ID alone, and grant IDs are not unique across funders: {@code GRANT_ID:"12345"}
 * returns articles funded by many unrelated organisations. A link is therefore VERIFIED only when the article's
 * own funding metadata lists the grant ID (or, for a bare ID, its longer cited form; see {@link GrantIds})
 * <em>and</em> a funder matching the grant's funder. Anything less is reported as UNCERTAIN with the reason, rather
 * than dropped or silently trusted.
 */
public final class AssociationVerifier {

    public record Assessment(Association association, String note) {

        static Assessment verified() {
            return new Assessment(Association.VERIFIED, null);
        }

        static Assessment uncertain(String note) {
            return new Assessment(Association.UNCERTAIN, note);
        }
    }

    private AssociationVerifier() {
    }

    public static Assessment assess(Grant grant, Article article) {
        List<FundingReference> sameId = article.fundingReferences().stream()
                .filter(reference -> GrantIds.refersTo(reference.grantId(), grant.id()))
                .toList();
        if (sameId.isEmpty()) {
            return Assessment.uncertain("Found by the grant ID search, but the article's funding metadata does not list "
                    + "grant ID " + grant.id() + ".");
        }
        if (grant.funder() == null) {
            return Assessment.uncertain("The grant record has no funder to compare with the article's funding metadata.");
        }

        boolean funderMatches = sameId.stream().anyMatch(reference ->
                FunderNames.match(reference.agency(), grant.funder())
                        || FunderNames.match(reference.agency(), grant.funderSearchTerm()));
        if (funderMatches) {
            return Assessment.verified();
        }

        String otherFunders = sameId.stream()
                .map(FundingReference::agency)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));
        return Assessment.uncertain(otherFunders.isEmpty()
                ? "The article cites grant ID " + grant.id() + " but does not name its funder."
                : "The article attributes grant ID " + grant.id() + " to " + otherFunders + ", not " + grant.funder() + ".");
    }

}
