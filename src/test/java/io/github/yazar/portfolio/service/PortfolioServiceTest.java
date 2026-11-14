package io.github.yazar.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.yazar.portfolio.europepmc.EuropePmcException;
import io.github.yazar.portfolio.europepmc.articles.Article;
import io.github.yazar.portfolio.europepmc.articles.ArticleSearchResult;
import io.github.yazar.portfolio.europepmc.articles.ArticlesClient;
import io.github.yazar.portfolio.europepmc.articles.FundingReference;
import io.github.yazar.portfolio.europepmc.grist.Grant;
import io.github.yazar.portfolio.europepmc.grist.GrantSearchResult;
import io.github.yazar.portfolio.europepmc.grist.GristClient;
import io.github.yazar.portfolio.europepmc.grist.Institution;
import io.github.yazar.portfolio.model.InstitutionPortfolio;
import io.github.yazar.portfolio.model.InstitutionPortfolio.GrantEntry;
import io.github.yazar.portfolio.model.InstitutionPortfolio.PublicationLookup;
import io.github.yazar.portfolio.model.LookupStatus;
import io.github.yazar.portfolio.model.PortfolioSummary;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PortfolioServiceTest {

    private static final String INSTITUTION = "University of Cambridge";
    private static final String FUNDER = "Wellcome Trust";

    private final GristClient grist = mock(GristClient.class);
    private final ArticlesClient articles = mock(ArticlesClient.class);
    private PortfolioService service;

    @AfterEach
    void stopLookupThreads() {
        service.close();
    }

    @Test
    void keepsEachResultWithItsGrantWhenLookupsFinishOutOfOrder() {
        givenGrants("A", "B", "C");
        // The first grant's lookup finishes last and the last grant's finishes first.
        when(articles.findByGrantId(eq("A"), anyInt())).thenAnswer(call -> {
            Thread.sleep(300);
            return resultFor("A", "pub-a");
        });
        when(articles.findByGrantId(eq("B"), anyInt())).thenAnswer(call -> {
            Thread.sleep(100);
            return resultFor("B", "pub-b");
        });
        when(articles.findByGrantId(eq("C"), anyInt())).thenReturn(resultFor("C", "pub-c"));
        service = new PortfolioService(grist, articles, 3, Duration.ofSeconds(5));

        InstitutionPortfolio portfolio = service.buildPortfolio(INSTITUTION, 10, 3);

        assertThat(portfolio.grants())
                .extracting(GrantEntry::rank, GrantEntry::grantId, entry -> entry.publications().items().getFirst().id())
                .containsExactly(tuple(1, "A", "pub-a"), tuple(2, "B", "pub-b"), tuple(3, "C", "pub-c"));
    }

    @Test
    void reportsAFailedLookupOnItsOwnGrantAndKeepsItDistinctFromNoneFound() {
        givenGrants("A", "B", "C");
        when(articles.findByGrantId(eq("A"), anyInt())).thenReturn(resultFor("A", "pub-a"));
        when(articles.findByGrantId(eq("B"), anyInt())).thenThrow(new EuropePmcException(ArticlesClient.API_NAME,
                EuropePmcException.Failure.UNAVAILABLE, "Europe PMC Articles API returned HTTP 503", null));
        when(articles.findByGrantId(eq("C"), anyInt())).thenReturn(resultFor("C"));
        service = new PortfolioService(grist, articles, 3, Duration.ofSeconds(5));

        InstitutionPortfolio portfolio = service.buildPortfolio(INSTITUTION, 10, 3);

        assertThat(portfolio.grants()).extracting(entry -> entry.publications().status())
                .containsExactly(LookupStatus.FOUND, LookupStatus.LOOKUP_FAILED, LookupStatus.NONE_FOUND);
        PublicationLookup failed = portfolio.grants().get(1).publications();
        assertThat(failed.error()).isEqualTo("Europe PMC Articles API returned HTTP 503");
        assertThat(failed.query()).isEqualTo("GRANT_ID:\"B\"");
        assertThat(portfolio.grants().get(2).publications().error()).isNull();
    }

    @Test
    void runsLookupsConcurrentlyButNeverMoreThanTheLimit() {
        int limit = 3;
        givenGrants("A", "B", "C", "D", "E", "F", "G", "H");
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CountDownLatch firstLookups = new CountDownLatch(limit);
        when(articles.findByGrantId(anyString(), anyInt())).thenAnswer(call -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                // The first lookups wait until `limit` of them are running at once,
                // which only happens if lookups really do overlap.
                firstLookups.countDown();
                firstLookups.await(2, TimeUnit.SECONDS);
                Thread.sleep(20);
                return resultFor(call.getArgument(0));
            } finally {
                inFlight.decrementAndGet();
            }
        });
        service = new PortfolioService(grist, articles, limit, Duration.ofSeconds(5));

        InstitutionPortfolio portfolio = service.buildPortfolio(INSTITUTION, 10, 3);

        assertThat(maxInFlight).hasValue(limit);
        assertThat(portfolio.grants()).hasSize(8)
                .allSatisfy(entry -> assertThat(entry.publications().status()).isEqualTo(LookupStatus.NONE_FOUND));
    }

    @Test
    void doesNotWaitForALookupThatMissesItsDeadline() {
        givenGrants("FAST", "SLOW");
        when(articles.findByGrantId(eq("FAST"), anyInt())).thenReturn(resultFor("FAST", "pub-fast"));
        when(articles.findByGrantId(eq("SLOW"), anyInt())).thenAnswer(call -> {
            Thread.sleep(5_000);
            return resultFor("SLOW", "pub-slow");
        });
        service = new PortfolioService(grist, articles, 2, Duration.ofMillis(300));

        long started = System.nanoTime();
        InstitutionPortfolio portfolio = service.buildPortfolio(INSTITUTION, 10, 3);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        assertThat(portfolio.grants().get(0).publications().status()).isEqualTo(LookupStatus.FOUND);
        assertThat(portfolio.grants().get(1).publications()).satisfies(lookup -> {
            assertThat(lookup.status()).isEqualTo(LookupStatus.LOOKUP_FAILED);
            assertThat(lookup.error()).contains("300 ms");
        });
    }

    @Test
    void neverStartsAQueuedLookupWhoseDeadlineHasPassed() {
        givenGrants("BUSY", "QUEUED");
        when(articles.findByGrantId(eq("BUSY"), anyInt())).thenAnswer(call -> {
            Thread.sleep(1_000);
            return resultFor("BUSY", "pub-busy");
        });
        when(articles.findByGrantId(eq("QUEUED"), anyInt())).thenReturn(resultFor("QUEUED", "pub-queued"));
        // One thread: QUEUED waits behind BUSY and its 200 ms deadline passes before a thread is free.
        service = new PortfolioService(grist, articles, 1, Duration.ofMillis(200));

        InstitutionPortfolio portfolio = service.buildPortfolio(INSTITUTION, 10, 3);

        assertThat(portfolio.grants()).extracting(entry -> entry.publications().status())
                .containsExactly(LookupStatus.LOOKUP_FAILED, LookupStatus.LOOKUP_FAILED);
        // When BUSY finishes and the thread frees up, the expired lookup must not call Europe PMC.
        verify(articles, after(1_500).never()).findByGrantId(eq("QUEUED"), anyInt());
    }

    @Test
    void returnsOnlyTheRequestedGrantsAndCountsSharedPublicationsOnce() {
        givenGrants("A", "B", "C");
        // One publication acknowledges both grant A and grant B.
        when(articles.findByGrantId(eq("A"), anyInt())).thenReturn(resultFor("A", "shared"));
        when(articles.findByGrantId(eq("B"), anyInt())).thenReturn(resultFor("B", "shared", "only-b"));
        service = new PortfolioService(grist, articles, 2, Duration.ofSeconds(5));

        InstitutionPortfolio portfolio = service.buildPortfolio(INSTITUTION, 2, 3);

        assertThat(portfolio.grants()).extracting(GrantEntry::grantId).containsExactly("A", "B");
        assertThat(portfolio.grantSearch().totalMatchingRecords()).isEqualTo(100);
        assertThat(portfolio.grantSearch().returnedGrants()).isEqualTo(2);
        PortfolioSummary summary = portfolio.summary();
        assertThat(summary.publicationAssociations()).isEqualTo(3);
        assertThat(summary.distinctPublications()).isEqualTo(2);
        assertThat(summary.grantsByFunder()).containsExactly(entry(FUNDER, 2L));
        verify(articles, never()).findByGrantId(eq("C"), anyInt());
    }

    private void givenGrants(String... grantIds) {
        List<Grant> grants = Arrays.stream(grantIds)
                .map(id -> new Grant(id, "Grant " + id, FUNDER, FUNDER,
                        new Institution(INSTITUTION, null, "https://ror.org/013meh722"), null, null))
                .toList();
        when(grist.findGrantsByInstitution(INSTITUTION))
                .thenReturn(new GrantSearchResult("aff:\"" + INSTITUTION + "\"", 100, grants));
    }

    /** A search result whose articles all acknowledge the grant, with its funder. */
    private static ArticleSearchResult resultFor(String grantId, String... articleIds) {
        List<Article> found = Arrays.stream(articleIds)
                .map(id -> new Article("MED", id, null, id, null, "Article " + id, "A journal", 2024,
                        List.of(new FundingReference(grantId, FUNDER))))
                .toList();
        return new ArticleSearchResult(ArticlesClient.queryFor(grantId), found.size(), found);
    }
}
