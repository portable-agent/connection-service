package dev.portableagent.connection.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("connection.token-keys")
public record TokenKeysProperties(boolean enabled, int currentVersion, List<TokenKeyItem> items) {
    public record TokenKeyItem(int version, String value) {
        @Override
        public String toString() {
            return "TokenKeyItem[version=" + version + ", value=<redacted>]";
        }
    }
}
