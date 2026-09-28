package dev.portableagent.connection.provider;

import dev.portableagent.connection.model.Provider;

public class ProviderNotConfigured extends RuntimeException {
    public ProviderNotConfigured(Provider provider) {
        super("OAuth provider is not configured: " + provider.value());
    }
}
