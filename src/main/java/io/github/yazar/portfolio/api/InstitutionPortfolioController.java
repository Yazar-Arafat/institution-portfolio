package io.github.yazar.portfolio.api;

import io.github.yazar.portfolio.model.InstitutionPortfolio;
import io.github.yazar.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "Institution portfolio")
public class InstitutionPortfolioController {

    // The request limits in one place. Defaults are text because @RequestParam takes them as strings.
    static final int MAX_INSTITUTION_LENGTH = 200;
    static final String DEFAULT_GRANTS = "5";
    static final int MAX_GRANTS = 10;
    static final String DEFAULT_PUBLICATIONS_PER_GRANT = "3";
    static final int MAX_PUBLICATIONS_PER_GRANT = 5;

    private final PortfolioService portfolioService;

    public InstitutionPortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping(path = "/institution-portfolio", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Funding awards for an institution, with linked publications",
            description = """
                    Searches the Europe PMC Grants API for grants whose award holder's affiliation matches the \
                    institution name, then looks up publications citing each grant in the Europe PMC Articles API. \
                    Publication lookups run concurrently; if one fails, the others are still returned and that \
                    grant is marked LOOKUP_FAILED.""")
    @ApiResponse(responseCode = "200", description = """
            Portfolio built. Individual publication lookups may still have failed: check each grant's publications.status.""")
    @ApiResponse(responseCode = "400", description = "Missing or invalid parameter",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "The Grants API failed or returned something unreadable",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "504", description = "The Grants API did not respond in time",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public InstitutionPortfolio getPortfolio(
            @Parameter(description = """
                    Institution or university name, matched against the award holder's affiliation. \
                    Must contain at least one letter or digit.""",
                    example = "University of Cambridge")
            @RequestParam
            @Size(max = MAX_INSTITUTION_LENGTH)
            String institution,

            @Parameter(description = "Maximum number of grants to return (1–" + MAX_GRANTS + ")")
            @RequestParam(defaultValue = DEFAULT_GRANTS)
            @Min(1) @Max(MAX_GRANTS)
            int limit,

            @Parameter(description = "Maximum number of publications to return per grant (1–"
                    + MAX_PUBLICATIONS_PER_GRANT + ")")
            @RequestParam(defaultValue = DEFAULT_PUBLICATIONS_PER_GRANT)
            @Min(1) @Max(MAX_PUBLICATIONS_PER_GRANT)
            int publicationsPerGrant) {

        // Checked here rather than with @Pattern: springdoc would copy a Java regex into the OpenAPI document,
        // where Swagger UI's JavaScript validation cannot run it.
        if (institution.codePoints().noneMatch(Character::isLetterOrDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "institution: must contain at least one letter or digit");
        }
        return portfolioService.buildPortfolio(institution.strip(), limit, publicationsPerGrant);
    }
}
