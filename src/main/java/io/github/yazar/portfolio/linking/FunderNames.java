package io.github.yazar.portfolio.linking;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares funder names across the two APIs, which spell funders differently:
 * "Medical Research Council" vs "UKRI | Medical Research Council", or "Wellcome" vs "Wellcome Trust".
 */
final class FunderNames {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    // UKRI (UK Research and Innovation) is the umbrella body for these councils, and articles often credit UKRI
    // instead of the council that awarded the grant: MRC grant MR/T030062/1 is cited with the funder "UKRI".
    private static final List<String> UKRI_NAMES = List.of("ukri", "uk research and innovation");
    private static final List<String> UKRI_COUNCILS = List.of(
            "arts and humanities research council", "biotechnology and biological sciences research council",
            "economic and social research council", "engineering and physical sciences research council",
            "medical research council", "natural environment research council",
            "science and technology facilities council", "innovate uk", "research england");

    private FunderNames() {
    }

    /**
     * True if the article's agency names the grant's funder. Compound agencies such as
     * "UKRI | Medical Research Council" are split on "|" and each part compared. Two names match when, ignoring
     * case and punctuation, one contains the other as whole words, or when one names UKRI and the other is UKRI or
     * one of its councils. This is deliberately lenient: it is only applied once the grant ID has already matched.
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
            if (!agency.isEmpty() && (containsWords(agency, funder) || containsWords(funder, agency)
                    || bothUkri(agency, funder))) {
                return true;
            }
        }
        return false;
    }

    /** True if one name is UKRI itself and the other is UKRI or one of its councils. */
    private static boolean bothUkri(String agency, String funder) {
        return (namesUkri(agency) && isUkriBody(funder)) || (namesUkri(funder) && isUkriBody(agency));
    }

    private static boolean namesUkri(String name) {
        return UKRI_NAMES.stream().anyMatch(ukri -> containsWords(name, ukri));
    }

    private static boolean isUkriBody(String name) {
        return namesUkri(name) || UKRI_COUNCILS.stream().anyMatch(council -> containsWords(name, council));
    }

    static String normalise(String name) {
        String words = NON_ALPHANUMERIC.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        return words.startsWith("the ") ? words.substring("the ".length()) : words;
    }

    private static boolean containsWords(String text, String words) {
        return (" " + text + " ").contains(" " + words + " ");
    }
}
