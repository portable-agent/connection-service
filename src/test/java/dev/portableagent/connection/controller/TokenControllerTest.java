package dev.portableagent.connection.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portableagent.connection.config.SecurityConfig;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.provider.AccessToken;
import dev.portableagent.connection.service.ConnectionRequired;
import dev.portableagent.connection.service.TokenService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TokenController.class)
@Import({SecurityConfig.class, ApiErrorHandler.class})
@TestPropertySource(
        properties = {
            "auth.issuer=http://identity.test/realms/portable-agent",
            "auth.jwks-url=http://identity.test/realms/portable-agent/certs",
            "auth.audience=connection-service",
            "auth.internal.clients[0]=action-service"
        })
class TokenControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TokenService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void getConnectionToken_whenServiceJwtIsAllowed_shouldReturnNoStoreToken() throws Exception {
        var tenantId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var expiresAt = Instant.parse("2026-09-29T12:00:00Z");
        when(service.issue(tenantId, actorId, Provider.GOOGLE_CALENDAR))
                .thenReturn(new AccessToken("access-secret", expiresAt));

        mockMvc.perform(post("/internal/v1/tokens")
                        .with(jwt().jwt(token -> token.claim("tenant_id", tenantId.toString())
                                        .claim("azp", "action-service"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_connection:token")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"actorId":"%s","provider":"google-calendar"}
                            """.formatted(actorId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").value("access-secret"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-29T12:00:00Z"));
    }

    @Test
    void getConnectionToken_whenScopeIsMissing_shouldReturnForbidden() throws Exception {
        performWithJwt("action-service", false).andExpect(status().isForbidden());
    }

    @Test
    void getConnectionToken_whenClientIsNotAllowed_shouldReturnForbidden() throws Exception {
        performWithJwt("unknown-service", true).andExpect(status().isForbidden());
    }

    @Test
    void getConnectionToken_withoutJwt_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getConnectionToken_whenConnectionIsMissing_shouldReturnConflictProblem() throws Exception {
        var tenantId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        when(service.issue(tenantId, actorId, Provider.GOOGLE_CALENDAR)).thenThrow(new ConnectionRequired());

        mockMvc.perform(post("/internal/v1/tokens")
                        .with(jwt().jwt(token -> token.claim("tenant_id", tenantId.toString())
                                        .claim("azp", "action-service"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_connection:token")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"actorId":"%s","provider":"google-calendar"}
                            """.formatted(actorId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://portable-agent.dev/problems/connection-required"));
    }

    @Test
    void safeTokenResponse_toString_shouldHideAccessToken() {
        var response = new SafeTokenResponse(
                "access-secret", Instant.parse("2026-09-29T12:00:00Z").atOffset(java.time.ZoneOffset.UTC));

        org.assertj.core.api.Assertions.assertThat(response.toString())
                .doesNotContain("access-secret")
                .contains("<redacted>");
    }

    private org.springframework.test.web.servlet.ResultActions performWithJwt(String clientId, boolean withScope)
            throws Exception {
        var request = jwt().jwt(token ->
                token.claim("tenant_id", UUID.randomUUID().toString()).claim("azp", clientId));
        if (withScope) {
            request.authorities(new SimpleGrantedAuthority("SCOPE_connection:token"));
        } else {
            request.authorities();
        }
        return mockMvc.perform(post("/internal/v1/tokens")
                .with(request)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()));
    }

    private String validBody() {
        return """
            {"actorId":"%s","provider":"google-calendar"}
            """.formatted(UUID.randomUUID());
    }
}
