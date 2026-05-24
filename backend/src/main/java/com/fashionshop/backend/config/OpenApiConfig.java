package com.fashionshop.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Fashion Shop API",
        version = "v1",
        description = "Backend APIs for Fashion Shop",
        contact = @Contact(name = "Fashion Shop Team")
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Bearer token fallback. Main flow uses HttpOnly cookie access_token."
)
@SecurityScheme(
    name = "cookieAuth",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "access_token",
    description = "Primary auth flow via HttpOnly cookie access_token."
)
public class OpenApiConfig {
}
