package io.github.yazar.portfolio.europepmc.grist;

import java.util.regex.Pattern;

/**
 * Builds a Grist affiliation query from a user-supplied institution name, e.g. {@code aff:"University of Cambridge"}.
 *
 * <p>The name is always sent as one quoted phrase. Unquoted, {@code aff:University of Cambridge} would mean
 * {@code aff:University} AND keyword {@code of} AND keyword {@code Cambridge}, because Grist treats terms without a
 * field as keyword searches. Characters that could end the phrase, or break the URL path the query travels in,
 * are replaced with spaces. Search indexes normally split words on punctuation anyway, so this should not change
 * what matches.
 */
public final class InstitutionQuery {

    // " and \ would end or escape the phrase; & ; = / ? # % have meaning in the URL path that carries the query.
    private static final Pattern RESERVED = Pattern.compile("[\"\\\\&;=/?#%\\p{Cntrl}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private InstitutionQuery() {
    }

    public static String forName(String institutionName) {
        String withoutReserved = RESERVED.matcher(institutionName).replaceAll(" ");
        String phrase = WHITESPACE.matcher(withoutReserved).replaceAll(" ").strip();
        if (phrase.isEmpty()) {
            throw new IllegalArgumentException("Institution name has no searchable characters: " + institutionName);
        }
        return "aff:\"" + phrase + "\"";
    }
}
