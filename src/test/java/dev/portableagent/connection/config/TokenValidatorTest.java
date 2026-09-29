package dev.portableagent.connection.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenValidatorTest {
    @Test
    void audienceValidator_whenRequiredAudienceExists_shouldAcceptToken() {
        var validator = new AudienceValidator("connection-service");

        assertThat(validator
                        .validate(jwt(
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString(),
                                List.of("connection-service")))
                        .hasErrors())
                .isFalse();
    }

    @Test
    void audienceValidator_whenAudienceDiffers_shouldRejectToken() {
        var validator = new AudienceValidator("connection-service");

        assertThat(validator
                        .validate(jwt(
                                UUID.randomUUID().toString(), UUID.randomUUID().toString(), List.of("other-service")))
                        .hasErrors())
                .isTrue();
    }

    @Test
    void identityValidator_whenSubjectOrTenantIsNotUuid_shouldRejectToken() {
        var validator = new IdentityValidator();

        assertThat(validator
                        .validate(jwt("not-a-uuid", UUID.randomUUID().toString(), List.of()))
                        .hasErrors())
                .isTrue();
        assertThat(validator
                        .validate(jwt(UUID.randomUUID().toString(), "not-a-uuid", List.of()))
                        .hasErrors())
                .isTrue();
    }

    @Test
    void identityValidator_whenIdentityIsValid_shouldAcceptToken() {
        var validator = new IdentityValidator();

        assertThat(validator
                        .validate(jwt(
                                UUID.randomUUID().toString(), UUID.randomUUID().toString(), List.of()))
                        .hasErrors())
                .isFalse();
    }

    private Jwt jwt(String subject, String tenantId, List<String> audience) {
        var now = Instant.parse("2026-09-28T13:00:00Z");
        return new Jwt(
                "token-value",
                now,
                now.plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("sub", subject, "tenant_id", tenantId, "aud", audience));
    }
}
