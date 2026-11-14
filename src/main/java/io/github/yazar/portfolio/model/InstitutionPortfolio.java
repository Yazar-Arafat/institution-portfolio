package io.github.yazar.portfolio.model;

import io.github.yazar.portfolio.europepmc.grist.Institution;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The response of {@code GET /institution-portfolio}. */
@Schema(description = "Grants matching an institution name, each with the publications linked to it")
public record InstitutionPortfolio(
        @Schema(description = "The institution name as requested", example = "University of Cambridge")
        String institution,
        GrantSearch grantSearch,
        @Schema(description = "Returned grants, in the Grants API's order")
        List<GrantEntry> grants,
        PortfolioSummary summary,
        @Schema(description = "How to interpret this data")
        List<String> caveats) {

    public record GrantSearch(
            @Schema(description = "Query sent to the Grants API", example = "aff:\"University of Cambridge\"")
            String query,
            @Schema(description = "Grants API records matching the query. Grist returns one record per investigator, "
                    + "so a grant is counted once for each of its investigators; this is not a count of grants.")
            Integer totalMatchingRecords,
            @Schema(description = "Distinct grants in this response. Can be fewer than limit, because only the first "
                    + "page of Grants API records is read.")
            int returnedGrants) {
    }

    public record GrantEntry(
            @Schema(description = "Position in the Grants API results, starting at 1")
            int rank,
            @Schema(example = "217100/Z/19/Z")
            String grantId,
            String title,
            @Schema(example = "Wellcome Trust")
            String funder,
            @Schema(description = "The award holder's institution as reported by the Grants API")
            Institution institution,
            String startDate,
            String endDate,
            PublicationLookup publications) {
    }

    public record PublicationLookup(
            LookupStatus status,
            @Schema(description = "Query sent to the Articles API", example = "GRANT_ID:\"217100/Z/19/Z\"")
            String query,
            @Schema(description = "Articles matching the grant ID in total; null if the lookup failed")
            Integer totalMatches,
            @Schema(description = "Returned publications, in the Articles API's order")
            List<Publication> items,
            @Schema(description = "Why the lookup failed; null otherwise")
            String error) {

        public static PublicationLookup of(String query, int totalMatches, List<Publication> items) {
            return new PublicationLookup(statusOf(items), query, totalMatches, items, null);
        }

        public static PublicationLookup failed(String query, String error) {
            return new PublicationLookup(LookupStatus.LOOKUP_FAILED, query, null, List.of(), error);
        }

        private static LookupStatus statusOf(List<Publication> items) {
            if (items.isEmpty()) {
                return LookupStatus.NONE_FOUND;
            }
            boolean anyVerified = items.stream().anyMatch(item -> item.association() == Association.VERIFIED);
            return anyVerified ? LookupStatus.FOUND : LookupStatus.UNCERTAIN;
        }
    }

    public record Publication(
            @Schema(description = "Europe PMC source; together with id it identifies the publication", example = "MED")
            String source,
            @Schema(example = "37860838")
            String id,
            String doi,
            String pmid,
            String pmcid,
            String title,
            @Schema(description = "Journal title, or the publisher for preprints and books")
            String journal,
            Integer publicationYear,
            @Schema(description = "The publication's page on Europe PMC")
            String url,
            Association association,
            @Schema(description = "Why the association is uncertain; null when verified")
            String associationNote) {
    }
}
