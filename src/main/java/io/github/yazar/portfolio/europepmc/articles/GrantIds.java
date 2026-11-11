package io.github.yazar.portfolio.europepmc.articles;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How grant IDs are cited in Europe PMC. Some funders' short IDs are also cited in a longer form: Wellcome grant
 * {@code 217100} appears both as {@code 217100} and as {@code 217100/Z/19/Z}, and searching for the short form alone
 * finds 12 articles instead of 26. So a <em>bare</em> ID (letters and digits only, with at least one digit) also
 * matches its longer {@code "/..."} form. IDs that already contain separators, such as {@code MR/P014178/1}, only
 * match exactly.
 */
public final class GrantIds {

    private static final Pattern BARE_ID = Pattern.compile("[A-Za-z]*[0-9][A-Za-z0-9]*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private GrantIds() {
    }

    public static boolean isBare(String grantId) {
        return BARE_ID.matcher(grantId).matches();
    }

    /** True if an ID cited by an article refers to the grant, ignoring case and spaces. */
    public static boolean refersTo(String citedId, String grantId) {
        if (citedId == null || grantId == null) {
            return false;
        }
        String cited = normalise(citedId);
        String grant = normalise(grantId);
        return cited.equals(grant) || (isBare(grant) && cited.startsWith(grant + "/"));
    }

    private static String normalise(String id) {
        return WHITESPACE.matcher(id).replaceAll("").toUpperCase(Locale.ROOT);
    }
}
