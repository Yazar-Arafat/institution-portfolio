package io.github.yazar.portfolio.europepmc.grist;

import java.util.List;

/**
 * @param query     the query sent to Grist, e.g. {@code aff:"University of Cambridge"}
 * @param totalHits how many Grist records matched in total, or null if Grist did not say
 * @param grants    the first page of matching grants, in Grist's order
 */
public record GrantSearchResult(String query, Integer totalHits, List<Grant> grants) {
}
