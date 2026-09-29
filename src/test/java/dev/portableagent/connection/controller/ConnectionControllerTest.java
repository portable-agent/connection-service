package dev.portableagent.connection.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portableagent.connection.config.SecurityConfig;
import dev.portableagent.connection.crypto.TokenOwner;
import dev.portableagent.connection.model.AccountConnection;
import dev.portableagent.connection.model.ConnectionStatus;
import dev.portableagent.connection.model.EncryptedToken;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.provider.ProviderCallFailed;
import dev.portableagent.connection.service.ConnectionNotFound;
import dev.portableagent.connection.service.ConnectionResult;
import dev.portableagent.connection.service.ConnectionService;
import dev.portableagent.connection.service.ConnectionStart;
import dev.portableagent.connection.service.InvalidOAuthCallback;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConnectionController.class)
@Import({SecurityConfig.class, ApiErrorHandler.class})
@TestPropertySource(
        properties = {
            "auth.issuer=http://identity.test/realms/portable-agent",
            "auth.jwks-url=http://identity.test/realms/portable-agent/certs",
            "auth.audience=connection-service"
        })
class ConnectionControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConnectionService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void startConnection_whenJwtIsValid_shouldUseItsOwnerAndDisableCaching() throws Exception {
        var tenantId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var expiresAt = Instant.parse("2026-09-28T14:00:00Z");
        when(service.start(new TokenOwner(tenantId, actorId, Provider.GOOGLE_CALENDAR)))
                .thenReturn(new ConnectionStart(URI.create("https://accounts.example/start?state=secret"), expiresAt));

        mockMvc.perform(post("/api/v1/connections/start")
                        .with(jwt().jwt(token ->
                                token.subject(actorId.toString()).claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"google-calendar\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(jsonPath("$.url").value("https://accounts.example/start?state=secret"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-28T14:00:00Z"));
    }

    @Test
    void listConnections_whenJwtIsValid_shouldReturnOnlyServiceResult() throws Exception {
        var tenantId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var connection = connection(tenantId, actorId);
        when(service.list(tenantId, actorId)).thenReturn(List.of(connection));

        mockMvc.perform(get("/api/v1/connections").with(jwt().jwt(token -> token.subject(actorId.toString())
                        .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(connection.id().toString()))
                .andExpect(jsonPath("$[0].provider").value("google-calendar"))
                .andExpect(jsonPath("$[0].status").value("active"));
    }

    @Test
    void disconnectConnection_whenOwned_shouldReturnNoContent() throws Exception {
        var tenantId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var connectionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/connections/{id}", connectionId)
                        .with(jwt().jwt(token ->
                                token.subject(actorId.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNoContent());

        verify(service).disconnect(tenantId, actorId, connectionId);
    }

    @Test
    void completeConnection_whenCodeIsPresent_shouldReturnStaticSafePage() throws Exception {
        when(service.finish("s".repeat(32), "secret-code", null)).thenReturn(ConnectionResult.CONNECTED);

        mockMvc.perform(get("/api/v1/connections/callback")
                        .queryParam("state", "s".repeat(32))
                        .queryParam("code", "secret-code"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Account connected")));
    }

    @Test
    void completeConnection_whenProviderDenied_shouldConsumeStateAndShowDeniedPage() throws Exception {
        when(service.finish("s".repeat(32), null, "access_denied")).thenReturn(ConnectionResult.DENIED);

        mockMvc.perform(get("/api/v1/connections/callback")
                        .queryParam("state", "s".repeat(32))
                        .queryParam("error", "access_denied"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not connected")));
    }

    @Test
    void completeConnection_whenCodeAndErrorArePresent_shouldReturnBadRequest() throws Exception {
        when(service.finish("s".repeat(32), "secret-code", "access_denied")).thenThrow(new InvalidOAuthCallback());

        mockMvc.perform(get("/api/v1/connections/callback")
                        .queryParam("state", "s".repeat(32))
                        .queryParam("code", "secret-code")
                        .queryParam("error", "access_denied"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://portable-agent.dev/problems/invalid-oauth-request"));
    }

    @Test
    void startConnection_withoutJwt_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/connections/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"google-calendar\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callback_whenProviderFails_shouldReturnSafeBadGatewayProblem() throws Exception {
        when(service.finish(any(), any(), any())).thenThrow(new ProviderCallFailed("Provider call failed"));

        mockMvc.perform(get("/api/v1/connections/callback")
                        .queryParam("state", "s".repeat(32))
                        .queryParam("code", "secret-code"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("https://portable-agent.dev/problems/provider-unavailable"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void disconnectConnection_whenMissing_shouldReturnNotFoundProblem() throws Exception {
        var tenantId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var connectionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ConnectionNotFound()).when(service).disconnect(tenantId, actorId, connectionId);

        mockMvc.perform(delete("/api/v1/connections/{id}", connectionId)
                        .with(jwt().jwt(token ->
                                token.subject(actorId.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://portable-agent.dev/problems/connection-not-found"));
    }

    private AccountConnection connection(UUID tenantId, UUID actorId) {
        var now = Instant.parse("2026-09-28T13:00:00Z");
        return new AccountConnection(
                UUID.randomUUID(),
                tenantId,
                actorId,
                Provider.GOOGLE_CALENDAR,
                "google-user",
                ConnectionStatus.ACTIVE,
                new EncryptedToken(new byte[] {1}, new byte[] {2}, 1),
                now,
                now);
    }
}
