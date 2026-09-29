package dev.portableagent.connection.config;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

final class InternalClientAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private static final String REQUIRED_SCOPE = "SCOPE_connection:token";

    private final InternalAuthProperties properties;

    InternalClientAuthorizationManager(InternalAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        var current = authentication.get();
        if (!(current instanceof JwtAuthenticationToken jwt) || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        var hasScope = current.getAuthorities().stream()
                .anyMatch(authority -> REQUIRED_SCOPE.equals(authority.getAuthority()));
        var clientId = jwt.getToken().getClaimAsString("azp");
        return new AuthorizationDecision(hasScope && properties.clients().contains(clientId));
    }
}
