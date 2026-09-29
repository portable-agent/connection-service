package dev.portableagent.connection.controller;

import dev.portableagent.connection.api.ConnectionsApi;
import dev.portableagent.connection.api.model.Connection;
import dev.portableagent.connection.api.model.StartRequest;
import dev.portableagent.connection.api.model.StartResponse;
import dev.portableagent.connection.crypto.TokenOwner;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.service.ConnectionResult;
import dev.portableagent.connection.service.ConnectionService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectionController implements ConnectionsApi {
    private static final String CONNECTED_PAGE =
            "<!doctype html><html><body><h1>Account connected</h1><p>You can close this page.</p></body></html>";
    private static final String DENIED_PAGE =
            "<!doctype html><html><body><h1>Account not connected</h1><p>You can close this page.</p></body></html>";
    private static final Map<ConnectionResult, String> RESULT_PAGES =
            Map.of(ConnectionResult.CONNECTED, CONNECTED_PAGE, ConnectionResult.DENIED, DENIED_PAGE);

    private final ConnectionService service;

    public ConnectionController(ConnectionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<StartResponse> startConnection(StartRequest request) {
        var jwt = jwt();
        var provider = Provider.fromValue(request.getProvider().getValue());
        var start = service.start(new TokenOwner(tenantId(jwt), actorId(jwt), provider));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ConnectionMapper.toResponse(start));
    }

    @Override
    public ResponseEntity<List<Connection>> listConnections() {
        var jwt = jwt();
        var connections = service.list(tenantId(jwt), actorId(jwt)).stream()
                .map(ConnectionMapper::toResponse)
                .toList();
        return ResponseEntity.ok(connections);
    }

    @Override
    public ResponseEntity<Void> disconnectConnection(UUID connectionId) {
        var jwt = jwt();
        service.disconnect(tenantId(jwt), actorId(jwt), connectionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<String> completeConnection(String state, String code, String error) {
        var result = service.finish(state, code, error);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(RESULT_PAGES.get(result));
    }

    private Jwt jwt() {
        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("JWT principal is missing");
    }

    private UUID tenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
