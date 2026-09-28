package dev.portableagent.connection.config;

import dev.portableagent.connection.provider.GoogleOAuthProvider;
import dev.portableagent.connection.provider.OAuthProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleOAuthConfig {
    @Bean
    @ConditionalOnProperty(prefix = "connection.google", name = "enabled", havingValue = "true")
    OAuthProvider googleOAuthProvider(RestClient.Builder builder, GoogleOAuthProperties properties) {
        return new GoogleOAuthProvider(builder.build(), properties, Clock.systemUTC());
    }
}
