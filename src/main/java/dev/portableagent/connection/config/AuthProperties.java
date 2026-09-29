package dev.portableagent.connection.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("auth")
public record AuthProperties(
        @NotNull URI issuer, @NotNull URI jwksUrl, @NotBlank String audience) {}
