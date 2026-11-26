package org.ikigaidigital.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String BASIC_AUTH = "basicAuth";

    /** Declares HTTP Basic, so Swagger UI shows an "Authorize" button and sends the credentials with each call. */
    @Bean
    OpenAPI timeDepositOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("XA Bank Time Deposit API")
                        .version("v1")
                        .description("""
                                Lists time deposits and applies monthly interest to their balances.
                                Authentication: HTTP Basic. GET needs role VIEWER, POST needs role OPERATOR.
                                Local demo users are listed in java/README.md."""))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
