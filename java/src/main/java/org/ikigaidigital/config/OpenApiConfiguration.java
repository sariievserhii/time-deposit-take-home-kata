package org.ikigaidigital.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String BASIC_AUTH = "basicAuth";

    /**
     * Declares HTTP Basic, so Swagger UI shows an "Authorize" button and sends the credentials with each call.
     *
     * @param loginHint shown at the top of the Swagger page. Set only in the dev profile (demo users), so reviewers see
     *                  how to log in where they need it. Empty everywhere else, so real deployments never advertise
     *                  credentials.
     */
    @Bean
    OpenAPI timeDepositOpenApi(@Value("${app.openapi.login-hint:}") String loginHint) {
        String description = """
                Lists time deposits and applies monthly interest to their balances.

                Authentication: HTTP Basic. GET needs role VIEWER, POST needs role OPERATOR.
                """ + (loginHint.isBlank() ? "" : "\n" + loginHint); // "\n", not the OS separator: Markdown output
        return new OpenAPI()
                .info(new Info()
                        .title("XA Bank Time Deposit API")
                        .version("v1")
                        .description(description))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
