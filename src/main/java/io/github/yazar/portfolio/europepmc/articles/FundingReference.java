package io.github.yazar.portfolio.europepmc.articles;

/**
 * One grant acknowledged by an article, as the article states it. Either field may be null.
 *
 * @param agency the funder's name, e.g. "Wellcome Trust" or "UKRI | Medical Research Council"
 */
public record FundingReference(String grantId, String agency) {
}
