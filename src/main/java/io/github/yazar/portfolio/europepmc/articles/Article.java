package io.github.yazar.portfolio.europepmc.articles;

import java.util.List;

/**
 * A publication from the Articles API. {@code source} plus {@code id} identify it uniquely, e.g. MED + 37860838
 * for a PubMed record or PPR + PPR1076173 for a preprint. Any field except the funding list may be null.
 *
 * @param journal           journal title, or the publisher for preprints and books
 * @param fundingReferences the grants the article acknowledges, from its own funding metadata
 */
public record Article(
        String source,
        String id,
        String doi,
        String pmid,
        String pmcid,
        String title,
        String journal,
        Integer publicationYear,
        List<FundingReference> fundingReferences) {

    /** Europe PMC's public page for an article is this prefix followed by {@code source/id}. */
    private static final String EUROPE_PMC_ARTICLE_PAGE = "https://europepmc.org/article/";

    /** The article's page on the Europe PMC website, or null if its source or ID is missing. */
    public String europePmcUrl() {
        if (source == null || id == null) {
            return null;
        }
        return EUROPE_PMC_ARTICLE_PAGE + source + "/" + id;
    }
}
