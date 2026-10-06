package dev.portableagent.connection.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portableagent.connection.provider.OAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GoogleOAuthConfigTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(GoogleOAuthConfig.class);

    @Test
    void context_whenGoogleIsDisabled_shouldNotCreateProvider() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(OAuthProvider.class));
    }

    @Test
    void context_whenGoogleConfigIsValid_shouldCreateProvider() {
        contextRunner.withPropertyValues(validProperties()).run(context -> assertThat(context)
                .hasSingleBean(OAuthProvider.class));
    }

    @Test
    void context_whenClientSecretIsMissing_shouldFailStartup() {
        contextRunner
                .withPropertyValues(
                        "connection.google.enabled=true",
                        "connection.google.authorization-url=https://accounts.test/auth",
                        "connection.google.token-url=https://oauth.test/token",
                        "connection.google.user-info-url=https://oauth.test/userinfo",
                        "connection.google.revoke-url=https://oauth.test/revoke",
                        "connection.google.client-id=client-id",
                        "connection.google.redirect-uri=https://agent.test/callback",
                        "connection.google.scopes[0]=openid")
                .run(context -> assertThat(context).hasFailed());
    }

    private String[] validProperties() {
        return new String[] {
            "connection.google.enabled=true",
            "connection.google.authorization-url=https://accounts.test/auth",
            "connection.google.token-url=https://oauth.test/token",
            "connection.google.user-info-url=https://oauth.test/userinfo",
            "connection.google.revoke-url=https://oauth.test/revoke",
            "connection.google.client-id=client-id",
            "connection.google.client-secret=client-secret",
            "connection.google.redirect-uri=https://agent.test/callback",
            "connection.google.scopes[0]=openid",
            "connection.google.scopes[1]=https://www.googleapis.com/auth/calendar.events"
        };
    }
}
