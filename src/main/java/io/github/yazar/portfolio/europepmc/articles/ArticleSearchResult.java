package io.github.yazar.portfolio.europepmc.articles;

import java.util.List;

/**
 * @param query    the query sent to the Articles API, e.g. {@code GRANT_ID:"217100/Z/19/Z"}
 * @param hitCount how many articles matched in total, which can be more than were returned
 * @param articles the returned articles, in the Articles API's order
 */
public record ArticleSearchResult(String query, int hitCount, List<Article> articles) {
}
