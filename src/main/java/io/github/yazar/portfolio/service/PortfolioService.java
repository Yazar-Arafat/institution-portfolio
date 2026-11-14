package io.github.yazar.portfolio.service;

import io.github.yazar.portfolio.config.EuropePmcProperties;
import io.github.yazar.portfolio.europepmc.EuropePmcException;
import io.github.yazar.portfolio.europepmc.articles.Article;
import io.github.yazar.portfolio.europepmc.articles.ArticleSearchResult;
import io.github.yazar.portfolio.europepmc.articles.ArticlesClient;
import io.github.yazar.portfolio.europepmc.grist.Grant;
import io.github.yazar.portfolio.europepmc.grist.GrantSearchResult;
import io.github.yazar.portfolio.europepmc.grist.GristClient;
import io.github.yazar.portfolio.linking.AssociationVerifier;
import io.github.yazar.portfolio.linking.AssociationVerifier.Assessment;
import io.github.yazar.portfolio.model.InstitutionPortfolio;
import io.github.yazar.portfolio.model.InstitutionPortfolio.GrantEntry;
import io.github.yazar.portfolio.model.InstitutionPortfolio.GrantSearch;
import io.github.yazar.portfolio.model.InstitutionPortfolio.Publication;
import io.github.yazar.portfolio.model.InstitutionPortfolio.PublicationLookup;
import io.github.yazar.portfolio.model.PortfolioSummary;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

/**
 * Builds an institution's funding portfolio: one Grants API search, then a publication lookup for each grant.
 *
 * <p>The lookups are independent, so they run concurrently on a small fixed thread pool. The pool is shared by
 * all incoming requests, so its size caps how many requests this service sends to the Articles API at once.
 * Each lookup has a deadline, and a lookup that fails or runs late is reported on its own grant without
 * affecting the others.
 */
@Service
public class PortfolioService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    static final List<String> CAVEATS = List.of(
            "Grants come from the Europe PMC Grants API, which only covers awards from participating funders. "
                    + "They are matched on institution name, so name variants can be missed and similarly named "
                    + "institutions included; check summary.matchedInstitutions. Only the first page of Grants API "
                    + "records is read, and it lists each grant once per investigator, so fewer grants than requested "
                    + "may be returned.",
            "Publications are found by searching the Articles API for each grant ID. VERIFIED means the article's "
                    + "own funding metadata lists the grant ID (or its longer cited form) and funder; UNCERTAIN means "
                    + "that could not be confirmed.",
            "NONE_FOUND does not prove a grant produced no publications: funding metadata on articles is incomplete.",
            "A publication can appear under more than one grant. It is listed under each, not merged.");

    private final GristClient gristClient;
    private final ArticlesClient articlesClient;
    private final ExecutorService lookupExecutor;
    private final Duration lookupTimeout;

    @Autowired
    public PortfolioService(GristClient gristClient, ArticlesClient articlesClient, EuropePmcProperties properties) {
        this(gristClient, articlesClient, properties.maxConcurrentLookups(), properties.lookupTimeout());
    }

    PortfolioService(GristClient gristClient, ArticlesClient articlesClient,
                     int maxConcurrentLookups, Duration lookupTimeout) {
        this.gristClient = gristClient;
        this.articlesClient = articlesClient;
        this.lookupTimeout = lookupTimeout;
        this.lookupExecutor = Executors.newFixedThreadPool(maxConcurrentLookups,
                Thread.ofPlatform().name("publication-lookup-", 1).daemon(true).factory());
    }

    /**
     * @throws EuropePmcException if the grant search fails; publication lookup failures are reported per grant instead
     */
    public InstitutionPortfolio buildPortfolio(String institution, int grantLimit, int publicationsPerGrant) {
        GrantSearchResult search = gristClient.findGrantsByInstitution(institution);
        List<Grant> grants = search.grants().stream().limit(grantLimit).toList();

        // Start every lookup before waiting for any, so they overlap. Joining in list order keeps each
        // result with its own grant, whatever order the lookups finish in.
        List<CompletableFuture<PublicationLookup>> lookups = grants.stream()
                .map(grant -> lookUpPublications(grant, publicationsPerGrant))
                .toList();

        List<GrantEntry> entries = new ArrayList<>(grants.size());
        for (int i = 0; i < grants.size(); i++) {
            entries.add(toEntry(i + 1, grants.get(i), lookups.get(i).join()));
        }

        return new InstitutionPortfolio(
                institution,
                new GrantSearch(search.query(), search.totalHits(), entries.size()),
                entries,
                PortfolioSummary.of(entries),
                CAVEATS);
    }

    private CompletableFuture<PublicationLookup> lookUpPublications(Grant grant, int maxPublications) {
        if (grant.id() == null) {
            return CompletableFuture.completedFuture(PublicationLookup.failed(null,
                    "The grant record has no ID, so its publications cannot be searched."));
        }
        return CompletableFuture
                .supplyAsync(() -> findPublications(grant, maxPublications), lookupExecutor)
                // At the deadline this future completes with a TimeoutException. A lookup still queued by then
                // is skipped when a thread frees up (the executor never runs work for a completed future),
                // so it never calls Europe PMC.
                .orTimeout(lookupTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(failure -> lookupFailed(grant, failure));
    }

    private PublicationLookup findPublications(Grant grant, int maxPublications) {
        ArticleSearchResult result = articlesClient.findByGrantId(grant.id(), maxPublications);

        // The same record twice under one grant adds nothing, so duplicates are dropped within a grant.
        // Nothing is merged across grants: one paper can legitimately acknowledge several grants.
        Map<String, Publication> publications = new LinkedHashMap<>();
        for (Article article : result.articles()) {
            String key = article.source() + ":" + article.id();
            publications.putIfAbsent(key, toPublication(article, AssociationVerifier.assess(grant, article)));
        }
        return PublicationLookup.of(result.query(), result.hitCount(), List.copyOf(publications.values()));
    }

    private PublicationLookup lookupFailed(Grant grant, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        String reason;
        if (cause instanceof TimeoutException) {
            reason = "Publication lookup did not finish within " + lookupTimeout.toMillis() + " ms";
            log.warn("Publication lookup for grant {} timed out", grant.id());
        } else if (cause instanceof EuropePmcException upstream) {
            reason = upstream.getMessage();
            log.warn("Publication lookup for grant {} failed: {} (cause: {})",
                    grant.id(), reason, NestedExceptionUtils.getMostSpecificCause(upstream).toString());
        } else {
            reason = "Unexpected error during publication lookup";
            log.error("Publication lookup for grant {} failed unexpectedly", grant.id(), cause);
        }
        return PublicationLookup.failed(ArticlesClient.queryFor(grant.id()), reason);
    }

    private static GrantEntry toEntry(int rank, Grant grant, PublicationLookup publications) {
        return new GrantEntry(rank, grant.id(), grant.title(), grant.funder(), grant.institution(),
                grant.startDate(), grant.endDate(), publications);
    }

    private static Publication toPublication(Article article, Assessment assessment) {
        return new Publication(article.source(), article.id(), article.doi(), article.pmid(), article.pmcid(),
                article.title(), article.journal(), article.publicationYear(), article.europePmcUrl(),
                assessment.association(), assessment.note());
    }

    @Override
    public void close() {
        lookupExecutor.shutdownNow();
    }
}
