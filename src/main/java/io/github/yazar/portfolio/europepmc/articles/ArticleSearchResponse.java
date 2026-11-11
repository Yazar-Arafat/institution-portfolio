package io.github.yazar.portfolio.europepmc.articles;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The parts of the Articles API's JSON response that this service reads; everything else is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
record ArticleSearchResponse(Integer hitCount, ResultList resultList) {

    ArticleSearchResult toResult(String query) {
        List<Article> articles = resultList == null || resultList.result() == null
                ? List.of()
                : resultList.result().stream().map(Result::toArticle).toList();
        return new ArticleSearchResult(query, hitCount == null ? articles.size() : hitCount, articles);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultList(List<Result> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(
            String id,
            String source,
            String pmid,
            String pmcid,
            String doi,
            String title,
            String pubYear,
            JournalInfo journalInfo,
            BookOrReportDetails bookOrReportDetails,
            GrantsList grantsList) {

        Article toArticle() {
            return new Article(source, id, doi, pmid, pmcid, title, journalOrPublisher(), year(), fundingReferences());
        }

        /** Journal articles have a journal title; preprints and books name a publisher instead (e.g. bioRxiv). */
        private String journalOrPublisher() {
            if (journalInfo != null && journalInfo.journal() != null && journalInfo.journal().title() != null) {
                return journalInfo.journal().title();
            }
            return bookOrReportDetails == null ? null : bookOrReportDetails.publisher();
        }

        private Integer year() {
            try {
                return pubYear == null ? null : Integer.valueOf(pubYear.strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private List<FundingReference> fundingReferences() {
            if (grantsList == null || grantsList.grant() == null) {
                return List.of();
            }
            return grantsList.grant().stream()
                    .map(grant -> new FundingReference(grant.grantId(), grant.agency()))
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JournalInfo(Journal journal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Journal(String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BookOrReportDetails(String publisher) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GrantsList(List<GrantReference> grant) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GrantReference(String grantId, String agency) {
    }
}
