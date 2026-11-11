package io.github.yazar.portfolio.linking;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares funder names across the two APIs, which spell funders differently:
 * "Medical Research Council" vs "UKRI | Medical Research Council", or "Wellcome" vs "Wellcome Trust".
 */
final class FunderNames {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    private FunderNames() {
    }

    /**
     * True if the article's agency names the grant's funder. Compound agencies such as
     * "UKRI | Medical Research Council" are split on "|" and each part compared. Two names match when, ignoring
     * case and punctuation, one contains the other as whole words. This is deliberately lenient: it is only
     * applied once the grant ID has already matched exactly.
     */
    static boolean match(String articleAgency, String grantFunder) {
        if (articleAgency == null || grantFunder == null) {
            return false;
        }
        String funder = normalise(grantFunder);
        if (funder.isEmpty()) {
            return false;
        }
        for (String part : articleAgency.split("\\|")) {
            String agency = normalise(part);
            if (!agency.isEmpty() && (containsWords(agency, funder) || containsWords(funder, agency))) {
                return true;
            }
        }
        return false;
    }

    static String normalise(String name) {
        String words = NON_ALPHANUMERIC.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        return words.startsWith("the ") ? words.substring("the ".length()) : words;
    }

    private static boolean containsWords(String text, String words) {
        return (" " + text + " ").contains(" " + words + " ");
    }
}
