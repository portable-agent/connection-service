package dev.portableagent.connection.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("connection.oauth")
public record OAuthProperties(Duration sessionTtl) {
    public OAuthProperties {
        Objects.requireNonNull(sessionTtl);
        if (sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new IllegalArgumentException("OAuth session TTL must be positive");
        }
    }
}
