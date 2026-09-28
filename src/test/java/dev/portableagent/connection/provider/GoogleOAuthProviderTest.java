package dev.portableagent.connection.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.portableagent.connection.config.GoogleOAuthProperties;
import dev.portableagent.connection.service.OAuthStart;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class GoogleOAuthProviderTest {
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private MockRestServiceServer server;
    private GoogleOAuthProvider provider;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GoogleOAuthProvider(builder.build(), properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authorizationUrl_whenSessionStarted_shouldContainOfflinePkceRequest() {
        var start = new OAuthStart("state-value", "challenge-value", "S256", NOW.plusSeconds(600));

        var url = provider.authorizationUrl(start);

        var params = UriComponentsBuilder.fromUri(url).build().getQueryParams();
        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(params.getFirst("client_id")).isEqualTo("client-id");
        assertThat(params.getFirst("redirect_uri")).isEqualTo("https://agent.test/oauth/callback");
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("state")).isEqualTo("state-value");
        assertThat(params.getFirst("code_challenge")).isEqualTo("challenge-value");
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(params.getFirst("access_type")).isEqualTo("offline");
        assertThat(params.getFirst("prompt")).isEqualTo("consent");
        assertThat(URLDecoder.decode(params.getFirst("scope"), StandardCharsets.UTF_8))
                .isEqualTo("openid https://www.googleapis.com/auth/calendar.events");
    }

    @Test
    void exchange_whenGoogleReturnsTokens_shouldLoadStableAccountId() {
        server.expect(once(), requestTo("https://oauth.test/token"))
                .andExpect(method(POST))
                .andExpect(content().string(containsString("code=code-1")))
                .andExpect(content().string(containsString("code_verifier=verifier-1")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\",\"expires_in\":3600,\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://oauth.test/userinfo"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andRespond(withSuccess("{\"sub\":\"google-user-1\"}", MediaType.APPLICATION_JSON));

        var result = provider.exchange("code-1", "verifier-1");

        assertThat(result.accountId()).isEqualTo("google-user-1");
        assertThat(result.refreshToken()).isEqualTo("refresh-1");
        assertThat(result.accessToken()).isEqualTo("access-1");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(result.toString()).doesNotContain("access-1", "refresh-1").contains("<redacted>");
        server.verify();
    }

    @Test
    void refresh_whenRefreshTokenIsValid_shouldReturnShortLivedAccessToken() {
        server.expect(once(), requestTo("https://oauth.test/token"))
                .andExpect(method(POST))
                .andExpect(content().string(containsString("refresh_token=refresh-1")))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-2\",\"expires_in\":1800,\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));

        var result = provider.refresh("refresh-1");

        assertThat(result.accessToken()).isEqualTo("access-2");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        server.verify();
    }

    @Test
    void revoke_whenRefreshTokenExists_shouldCallConfiguredEndpoint() {
        server.expect(once(), requestTo("https://oauth.test/revoke"))
                .andExpect(method(POST))
                .andExpect(content().string("token=refresh-1"))
                .andRespond(withNoContent());

        provider.revoke("refresh-1");

        server.verify();
    }

    @Test
    void properties_whenPrinted_shouldHideClientSecret() {
        assertThat(properties().toString()).doesNotContain("client-secret").contains("<redacted>");
    }

    @Test
    void constructor_whenExtraScopeIsConfigured_shouldRejectIt() {
        var base = properties();
        var unsafe = new GoogleOAuthProperties(
                true,
                base.authorizationUrl(),
                base.tokenUrl(),
                base.userInfoUrl(),
                base.revokeUrl(),
                base.clientId(),
                base.clientSecret(),
                base.redirectUri(),
                List.of("openid", "https://www.googleapis.com/auth/calendar.events", "drive"));

        assertThatThrownBy(() -> new GoogleOAuthProvider(RestClient.create(), unsafe, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only openid and calendar.events");
    }

    private GoogleOAuthProperties properties() {
        return new GoogleOAuthProperties(
                true,
                URI.create("https://accounts.test/auth"),
                URI.create("https://oauth.test/token"),
                URI.create("https://oauth.test/userinfo"),
                URI.create("https://oauth.test/revoke"),
                "client-id",
                "client-secret",
                URI.create("https://agent.test/oauth/callback"),
                List.of("openid", "https://www.googleapis.com/auth/calendar.events"));
    }
}
