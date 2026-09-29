package dev.portableagent.connection.controller;

import dev.portableagent.connection.api.TokensApi;
import dev.portableagent.connection.api.model.TokenRequest;
import dev.portableagent.connection.api.model.TokenResponse;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.service.TokenService;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TokenController implements TokensApi {
    private final TokenService service;

    public TokenController(TokenService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<TokenResponse> getConnectionToken(TokenRequest request) {
        var jwt = jwt();
        var token = service.issue(
                UUID.fromString(jwt.getClaimAsString("tenant_id")),
                request.getActorId(),
                Provider.fromValue(request.getProvider().getValue()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new SafeTokenResponse(
                        token.accessToken(), token.expiresAt().atOffset(ZoneOffset.UTC)));
    }

    private Jwt jwt() {
        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("JWT principal is missing");
    }
}
