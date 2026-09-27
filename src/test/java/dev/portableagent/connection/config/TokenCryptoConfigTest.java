package dev.portableagent.connection.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portableagent.connection.crypto.TokenCipher;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TokenCryptoConfigTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TokenCryptoConfig.class);

    @Test
    void context_whenCryptoIsDisabled_shouldNotCreateCipher() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(TokenCipher.class));
    }

    @Test
    void context_whenKeysAreValid_shouldCreateCipher() {
        var key = Base64.getEncoder().encodeToString(new byte[32]);

        contextRunner
                .withPropertyValues(
                        "connection.token-keys.enabled=true",
                        "connection.token-keys.current-version=2",
                        "connection.token-keys.items[0].version=1",
                        "connection.token-keys.items[0].value=" + key,
                        "connection.token-keys.items[1].version=2",
                        "connection.token-keys.items[1].value=" + key)
                .run(context -> assertThat(context).hasSingleBean(TokenCipher.class));
    }

    @Test
    void context_whenCurrentKeyIsMissing_shouldFailStartup() {
        var key = Base64.getEncoder().encodeToString(new byte[32]);

        contextRunner
                .withPropertyValues(
                        "connection.token-keys.enabled=true",
                        "connection.token-keys.current-version=2",
                        "connection.token-keys.items[0].version=1",
                        "connection.token-keys.items[0].value=" + key)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void properties_whenPrinted_shouldHideKeyValue() {
        var properties =
                new TokenKeysProperties(true, 1, List.of(new TokenKeysProperties.TokenKeyItem(1, "secret-key-value")));

        assertThat(properties.toString()).doesNotContain("secret-key-value").contains("<redacted>");
    }
}
