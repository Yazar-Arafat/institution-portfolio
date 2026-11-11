package io.github.yazar.portfolio.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** How confident the service is that a publication belongs to a grant. */
@Schema(description = """
        VERIFIED: the article's own funding metadata lists this grant ID (or its longer cited form) and funder. \
        UNCERTAIN: found by the grant ID search, but the ID or funder could not be confirmed; see associationNote.""")
public enum Association {
    VERIFIED,
    UNCERTAIN
}
