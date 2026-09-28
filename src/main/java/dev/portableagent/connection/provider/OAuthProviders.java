package dev.portableagent.connection.provider;

import dev.portableagent.connection.model.Provider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OAuthProviders {
    private final Map<Provider, OAuthProvider> providers;

    public OAuthProviders(List<OAuthProvider> providers) {
        var byType = new EnumMap<Provider, OAuthProvider>(Provider.class);
        for (var provider : providers) {
            if (byType.putIfAbsent(provider.type(), provider) != null) {
                throw new IllegalArgumentException("OAuth provider is duplicated: " + provider.type());
            }
        }
        this.providers = Map.copyOf(byType);
    }

    public OAuthProvider get(Provider type) {
        var provider = providers.get(type);
        if (provider == null) {
            throw new ProviderNotConfigured(type);
        }
        return provider;
    }
}
