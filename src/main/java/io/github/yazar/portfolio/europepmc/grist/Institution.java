package io.github.yazar.portfolio.europepmc.grist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The award holder's institution, as reported on a grant. Any field may be null. */
public record Institution(
        @Schema(example = "University of Cambridge") String name,
        String department,
        @Schema(description = "Research Organization Registry ID, when Grist provides one",
                example = "ror.org/013meh722") String rorId) {
}
