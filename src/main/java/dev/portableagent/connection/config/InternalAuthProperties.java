package dev.portableagent.connection.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.internal")
public record InternalAuthProperties(Set<String> clients) {
    public InternalAuthProperties {
        clients = clients == null ? Set.of() : Set.copyOf(clients);
    }
}
