package dev.portableagent.connection.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.portableagent.connection.model.Provider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthProvidersTest {
    @Mock
    OAuthProvider google;

    @Mock
    OAuthProvider duplicate;

    @Test
    void get_whenProviderExists_shouldReturnStrategy() {
        when(google.type()).thenReturn(Provider.GOOGLE_CALENDAR);
        var providers = new OAuthProviders(List.of(google));

        assertThat(providers.get(Provider.GOOGLE_CALENDAR)).isSameAs(google);
    }

    @Test
    void get_whenProviderIsDisabled_shouldRejectIt() {
        var providers = new OAuthProviders(List.of());

        assertThatThrownBy(() -> providers.get(Provider.GOOGLE_CALENDAR))
                .isInstanceOf(ProviderNotConfigured.class)
                .hasMessage("OAuth provider is not configured: google-calendar");
    }

    @Test
    void constructor_whenStrategiesHaveSameType_shouldRejectConfig() {
        when(google.type()).thenReturn(Provider.GOOGLE_CALENDAR);
        when(duplicate.type()).thenReturn(Provider.GOOGLE_CALENDAR);

        assertThatThrownBy(() -> new OAuthProviders(List.of(google, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GOOGLE_CALENDAR");
    }
}
