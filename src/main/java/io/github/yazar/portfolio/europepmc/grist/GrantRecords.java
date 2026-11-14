package io.github.yazar.portfolio.europepmc.grist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Grist returns one record per investigator, so a grant with several investigators appears several times, each time
 * with that investigator's institution. A search for "University of Manchester" returned grant 352259 eight times,
 * under Bristol, Liverpool, Mulago and others, because one of its investigators is at Manchester.
 */
final class GrantRecords {

    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private GrantRecords() {
    }

    /**
     * Merges records that share a grant ID into one grant, in the order each grant first appears. The merged grant
     * reports the first institution whose name contains the searched name, since that is why the grant matched, or
     * the first record's institution if none does ({@code aff:} also searches departments, which Grist does not
     * return). Records without an ID cannot be matched with others, so each one is kept on its own.
     */
    static List<Grant> mergeByGrantId(List<Grant> records, String searchedName) {
        List<List<Grant>> groups = new ArrayList<>();
        Map<String, List<Grant>> groupsById = new HashMap<>();
        for (Grant record : records) {
            if (record.id() == null) {
                groups.add(List.of(record));
                continue;
            }
            List<Grant> group = groupsById.get(record.id());
            if (group == null) {
                group = new ArrayList<>();
                groupsById.put(record.id(), group);
                groups.add(group);
            }
            group.add(record);
        }
        String searched = normalise(searchedName);
        return groups.stream().map(group -> merge(group, searched)).toList();
    }

    private static Grant merge(List<Grant> records, String searched) {
        Grant first = records.getFirst();
        Institution institution = records.stream()
                .map(Grant::institution)
                .filter(candidate -> isNamed(candidate, searched))
                .findFirst()
                .orElse(first.institution());
        return new Grant(first.id(), first.title(), first.funder(), first.funderSearchTerm(),
                institution, first.startDate(), first.endDate());
    }

    private static boolean isNamed(Institution institution, String searched) {
        return institution != null && institution.name() != null
                && (" " + normalise(institution.name()) + " ").contains(" " + searched + " ");
    }

    /** Lower case, with each run of characters other than letters and digits turned into a single space. */
    private static String normalise(String name) {
        return NOT_LETTER_OR_DIGIT.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
