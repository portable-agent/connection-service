package dev.portableagent.connection.provider;

import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.service.OAuthStart;
import java.net.URI;

public interface OAuthProvider {
    Provider type();

    URI authorizationUrl(OAuthStart start);

    ProviderTokens exchange(String code, String verifier);

    AccessToken refresh(String refreshToken);

    void revoke(String refreshToken);
}
