package dev.portableagent.connection.controller;

import dev.portableagent.connection.api.model.Connection;
import dev.portableagent.connection.api.model.StartResponse;
import dev.portableagent.connection.model.AccountConnection;
import dev.portableagent.connection.service.ConnectionStart;
import java.time.ZoneOffset;

final class ConnectionMapper {
    private ConnectionMapper() {}

    static StartResponse toResponse(ConnectionStart start) {
        return new StartResponse(start.url(), start.expiresAt().atOffset(ZoneOffset.UTC));
    }

    static Connection toResponse(AccountConnection connection) {
        return new Connection(
                connection.id(),
                Connection.ProviderEnum.fromValue(connection.provider().value()),
                Connection.StatusEnum.fromValue(connection.status().value()),
                connection.createdAt().atOffset(ZoneOffset.UTC));
    }
}
