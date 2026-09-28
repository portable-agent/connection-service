package dev.portableagent.connection.provider;

import java.time.Instant;

public record AccessToken(String accessToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "AccessToken[accessToken=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
