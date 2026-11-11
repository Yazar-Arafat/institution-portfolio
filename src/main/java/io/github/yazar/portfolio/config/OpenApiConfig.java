package io.github.yazar.portfolio.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** Title and description shown in Swagger UI and in the OpenAPI document at /v3/api-docs. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Institution Funding Portfolio API",
        version = "0.1.0",
        description = """
                Finds funding awards associated with an institution in the Europe PMC Grants API, and links each \
                grant to publications in the Europe PMC Articles API. Results are a sample of what Europe PMC \
                indexes, not an institution's complete funding portfolio."""))
public class OpenApiConfig {
}
