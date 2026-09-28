package dev.portableagent.connection.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.portableagent.connection.config.GoogleOAuthProperties;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.service.OAuthStart;
import java.net.URI;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

public final class GoogleOAuthProvider implements OAuthProvider {
    private static final Set<String> ALLOWED_SCOPES =
            Set.of("openid", "https://www.googleapis.com/auth/calendar.events");
    private final RestClient client;
    private final GoogleOAuthProperties properties;
    private final Clock clock;

    public GoogleOAuthProvider(RestClient client, GoogleOAuthProperties properties, Clock clock) {
        this.client = Objects.requireNonNull(client);
        this.properties = requireConfigured(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Provider type() {
        return Provider.GOOGLE_CALENDAR;
    }

    @Override
    public URI authorizationUrl(OAuthStart start) {
        return UriComponentsBuilder.fromUri(properties.authorizationUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", properties.scopes()))
                .queryParam("state", start.state())
                .queryParam("code_challenge", start.challenge())
                .queryParam("code_challenge_method", start.method())
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .build()
                .encode()
                .toUri();
    }

    @Override
    public ProviderTokens exchange(String code, String verifier) {
        var form = baseForm();
        form.add("code", required(code, "Authorization code"));
        form.add("code_verifier", required(verifier, "PKCE verifier"));
        form.add("redirect_uri", properties.redirectUri().toString());
        form.add("grant_type", "authorization_code");
        var token = tokenRequest(form, true);
        var accountId = userInfo(token.accessToken());
        return new ProviderTokens(
                accountId,
                token.refreshToken(),
                token.accessToken(),
                clock.instant().plusSeconds(token.expiresIn()));
    }

    @Override
    public AccessToken refresh(String refreshToken) {
        var form = baseForm();
        form.add("refresh_token", required(refreshToken, "Refresh token"));
        form.add("grant_type", "refresh_token");
        var token = tokenRequest(form, false);
        return new AccessToken(token.accessToken(), clock.instant().plusSeconds(token.expiresIn()));
    }

    @Override
    public void revoke(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("token", required(refreshToken, "Refresh token"));
        try {
            client.post()
                    .uri(properties.revokeUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException error) {
            throw new ProviderCallFailed("Google OAuth revoke failed");
        }
    }

    private TokenResponse tokenRequest(LinkedMultiValueMap<String, String> form, boolean requireRefreshToken) {
        try {
            var response = client.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null
                    || response.accessToken() == null
                    || response.accessToken().isBlank()
                    || response.expiresIn() <= 0
                    || !"Bearer".equalsIgnoreCase(response.tokenType())
                    || (requireRefreshToken
                            && (response.refreshToken() == null
                                    || response.refreshToken().isBlank()))) {
                throw new ProviderCallFailed("Google OAuth token response is incomplete");
            }
            return response;
        } catch (RestClientException error) {
            throw new ProviderCallFailed("Google OAuth token request failed");
        }
    }

    private String userInfo(String accessToken) {
        try {
            var response = client.get()
                    .uri(properties.userInfoUrl())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(UserInfo.class);
            if (response == null || response.sub() == null || response.sub().isBlank()) {
                throw new ProviderCallFailed("Google user info response is incomplete");
            }
            return response.sub();
        } catch (RestClientException error) {
            throw new ProviderCallFailed("Google user info request failed");
        }
    }

    private LinkedMultiValueMap<String, String> baseForm() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }

    private GoogleOAuthProperties requireConfigured(GoogleOAuthProperties value) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(value.authorizationUrl());
        Objects.requireNonNull(value.tokenUrl());
        Objects.requireNonNull(value.userInfoUrl());
        Objects.requireNonNull(value.revokeUrl());
        Objects.requireNonNull(value.redirectUri());
        required(value.clientId(), "Google client id");
        required(value.clientSecret(), "Google client secret");
        if (value.scopes() == null || !Set.copyOf(value.scopes()).equals(ALLOWED_SCOPES)) {
            throw new IllegalArgumentException("Google scopes must contain only openid and calendar.events");
        }
        return value;
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType) {
        @Override
        public String toString() {
            return "TokenResponse[accessToken=<redacted>, refreshToken=<redacted>, expiresIn=" + expiresIn
                    + ", tokenType=" + tokenType + "]";
        }
    }

    private record UserInfo(String sub) {}
}
