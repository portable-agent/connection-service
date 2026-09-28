package dev.portableagent.connection.service;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record ConnectionStart(URI url, Instant expiresAt) {
    public ConnectionStart {
        Objects.requireNonNull(url);
        Objects.requireNonNull(expiresAt);
    }

    @Override
    public String toString() {
        return "ConnectionStart[url=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
