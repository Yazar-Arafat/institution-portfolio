package io.github.yazar.portfolio.model;

import io.github.yazar.portfolio.europepmc.grist.Institution;
import io.github.yazar.portfolio.model.InstitutionPortfolio.GrantEntry;
import io.github.yazar.portfolio.model.InstitutionPortfolio.Publication;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Schema(description = "Counts over the grants and publications in this response only")
public record PortfolioSummary(
        @Schema(description = "What these counts cover")
        String note,
        int grantsReturned,
        @Schema(description = "Returned grants per funder")
        Map<String, Long> grantsByFunder,
        @Schema(description = "Returned grants per publication lookup status")
        Map<LookupStatus, Long> grantsByPublicationStatus,
        @Schema(description = "Grant–publication pairs; a publication listed under two grants counts twice")
        int publicationAssociations,
        int verifiedAssociations,
        int uncertainAssociations,
        @Schema(description = "Distinct publications (by source and id) across all returned grants")
        int distinctPublications,
        @Schema(description = "Institutions named on the returned grants, showing what the name search matched")
        List<MatchedInstitution> matchedInstitutions) {

    static final String NOTE = "Counts cover only the grants and publications in this response. "
            + "They are not the institution's complete funding portfolio.";
    static final String UNKNOWN_FUNDER = "Unknown funder";

    public record MatchedInstitution(String name, String rorId, long grants) {
    }

    public static PortfolioSummary of(List<GrantEntry> grants) {
        List<Publication> associations = grants.stream()
                .flatMap(grant -> grant.publications().items().stream())
                .toList();
        int verified = (int) associations.stream()
                .filter(publication -> publication.association() == Association.VERIFIED)
                .count();
        int distinct = (int) associations.stream()
                .map(publication -> publication.source() + ":" + publication.id())
                .distinct()
                .count();

        return new PortfolioSummary(
                NOTE,
                grants.size(),
                countByFunder(grants),
                countByStatus(grants),
                associations.size(),
                verified,
                associations.size() - verified,
                distinct,
                matchedInstitutions(grants));
    }

    private static Map<String, Long> countByFunder(List<GrantEntry> grants) {
        return grants.stream().collect(Collectors.groupingBy(
                grant -> Objects.requireNonNullElse(grant.funder(), UNKNOWN_FUNDER),
                LinkedHashMap::new,
                Collectors.counting()));
    }

    /** Every status is listed, with zero counts included, so the shape of the summary is predictable. */
    private static Map<LookupStatus, Long> countByStatus(List<GrantEntry> grants) {
        Map<LookupStatus, Long> counts = new EnumMap<>(LookupStatus.class);
        for (LookupStatus status : LookupStatus.values()) {
            counts.put(status, 0L);
        }
        grants.forEach(grant -> counts.merge(grant.publications().status(), 1L, Long::sum));
        return counts;
    }

    private static List<MatchedInstitution> matchedInstitutions(List<GrantEntry> grants) {
        Map<InstitutionKey, Long> counts = grants.stream()
                .map(GrantEntry::institution)
                .filter(institution -> institution != null && institution.name() != null)
                .collect(Collectors.groupingBy(InstitutionKey::of, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> new MatchedInstitution(entry.getKey().name(), entry.getKey().rorId(), entry.getValue()))
                .toList();
    }

    /** Groups by name and ROR ID only: one institution's grants can come from different departments. */
    private record InstitutionKey(String name, String rorId) {
        static InstitutionKey of(Institution institution) {
            return new InstitutionKey(institution.name(), institution.rorId());
        }
    }
}
