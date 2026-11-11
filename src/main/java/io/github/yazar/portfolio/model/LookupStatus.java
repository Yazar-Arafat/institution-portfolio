package io.github.yazar.portfolio.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** The outcome of looking up a grant's publications. */
@Schema(description = """
        FOUND: at least one publication is VERIFIED. \
        UNCERTAIN: publications were found by grant ID, but none could be verified. \
        NONE_FOUND: no publications cite this grant ID (this does not prove none exist). \
        LOOKUP_FAILED: the search failed or timed out; see the error field.""")
public enum LookupStatus {
    FOUND,
    UNCERTAIN,
    NONE_FOUND,
    LOOKUP_FAILED
}
