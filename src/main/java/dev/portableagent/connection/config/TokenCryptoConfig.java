package dev.portableagent.connection.config;

import dev.portableagent.connection.crypto.AesTokenCipher;
import dev.portableagent.connection.crypto.TokenCipher;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TokenKeysProperties.class)
public class TokenCryptoConfig {
    @Bean
    @ConditionalOnProperty(prefix = "connection.token-keys", name = "enabled", havingValue = "true")
    TokenCipher tokenCipher(TokenKeysProperties properties) {
        var keys = new HashMap<Integer, byte[]>();
        if (properties.items() != null) {
            for (var item : properties.items()) {
                if (keys.putIfAbsent(item.version(), decode(item.value())) != null) {
                    throw new IllegalArgumentException("Token key version is duplicated: " + item.version());
                }
            }
        }
        return new AesTokenCipher(properties.currentVersion(), keys, new SecureRandom());
    }

    private byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Token key value must not be blank");
        }
        return Base64.getDecoder().decode(value);
    }
}
