package com.example.coach.config;

import com.example.coach.security.CurrentUserId;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/** Swagger UI is served at /swagger-ui.html. Use "Authorize" with a token from /api/auth/login. */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "AI Interview Coach API", version = "v1",
                description = "Resume vs job matching, resume rewrites and an adaptive AI mock interviewer."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    static {
        // The user id comes from the JWT, not from the request, so hide it from the generated docs.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }
}
