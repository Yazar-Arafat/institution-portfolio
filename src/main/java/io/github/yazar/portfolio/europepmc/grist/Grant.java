package io.github.yazar.portfolio.europepmc.grist;

/**
 * A grant as reported by Grist. Any field may be null when Grist leaves it out.
 *
 * @param funder           the funder's name, e.g. "Wellcome Trust"
 * @param funderSearchTerm the name Grist suggests for finding this funder's publications
 */
public record Grant(
        String id,
        String title,
        String funder,
        String funderSearchTerm,
        Institution institution,
        String startDate,
        String endDate) {
}
