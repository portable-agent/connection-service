package dev.portableagent.connection.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("connection.google")
public record GoogleOAuthProperties(
        boolean enabled,
        URI authorizationUrl,
        URI tokenUrl,
        URI userInfoUrl,
        URI revokeUrl,
        String clientId,
        String clientSecret,
        URI redirectUri,
        List<String> scopes) {
    public GoogleOAuthProperties {
        scopes = scopes == null ? null : List.copyOf(scopes);
    }

    @Override
    public String toString() {
        return "GoogleOAuthProperties[enabled=" + enabled
                + ", authorizationUrl=" + authorizationUrl
                + ", tokenUrl=" + tokenUrl
                + ", userInfoUrl=" + userInfoUrl
                + ", revokeUrl=" + revokeUrl
                + ", clientId=" + clientId
                + ", clientSecret=<redacted>, redirectUri=" + redirectUri
                + ", scopes=" + scopes + "]";
    }
}
