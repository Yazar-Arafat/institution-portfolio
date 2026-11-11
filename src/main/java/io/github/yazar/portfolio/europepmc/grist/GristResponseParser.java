package io.github.yazar.portfolio.europepmc.grist;

import io.github.yazar.portfolio.europepmc.EuropePmcException;
import io.github.yazar.portfolio.europepmc.EuropePmcException.Failure;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Reads Grist's JSON defensively. The JSON is converted from XML and its shape has changed over time
 * (older responses gave the funder as a plain string), so each field is looked up individually instead of
 * binding to a strict schema. Missing fields become null, and a missing record list means no grants.
 */
final class GristResponseParser {

    private GristResponseParser() {
    }

    static GrantSearchResult parse(String query, JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new EuropePmcException(GristClient.API_NAME, Failure.INVALID_RESPONSE,
                    GristClient.API_NAME + " returned a response that could not be read", null);
        }
        List<Grant> grants = records(root.path("RecordList").path("Record")).stream()
                .map(GristResponseParser::toGrant)
                .toList();
        return new GrantSearchResult(query, toInteger(text(root.path("HitCount"))), grants);
    }

    /** XML-derived JSON may give a single record as an object rather than a one-element array. */
    private static List<JsonNode> records(JsonNode node) {
        if (node.isArray()) {
            return List.copyOf(node.values());
        }
        if (node.isObject()) {
            return List.of(node);
        }
        return List.of();
    }

    private static Grant toGrant(JsonNode record) {
        JsonNode grant = record.path("Grant");
        JsonNode funder = first(grant.path("Funder"));
        JsonNode institution = first(record.path("Institution"));
        return new Grant(
                text(grant.path("Id")),
                text(grant.path("Title")),
                funder.isObject() ? text(funder.path("Name")) : text(funder),
                text(funder.path("pubMedSearchTerm")),
                new Institution(
                        text(institution.path("Name")),
                        text(institution.path("Department")),
                        text(institution.path("RORID"))),
                text(grant.path("StartDate")),
                text(grant.path("EndDate")));
    }

    /**
     * XML-derived JSON gives a repeated element as an array. Every grant seen so far has one funder and one
     * institution, so if several are listed, the first is used.
     */
    private static JsonNode first(JsonNode node) {
        return node.isArray() ? node.path(0) : node;
    }

    /** The node's text, or null if it is missing, null, blank or not a single value. */
    private static String text(JsonNode node) {
        if (!node.isValueNode() || node.isNull()) {
            return null;
        }
        String value = node.asString().strip();
        return value.isEmpty() ? null : value;
    }

    private static Integer toInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
