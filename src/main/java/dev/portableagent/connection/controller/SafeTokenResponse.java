package dev.portableagent.connection.controller;

import dev.portableagent.connection.api.model.TokenResponse;
import java.time.OffsetDateTime;

final class SafeTokenResponse extends TokenResponse {
    SafeTokenResponse(String accessToken, OffsetDateTime expiresAt) {
        super(accessToken, TokenTypeEnum.BEARER, expiresAt);
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=<redacted>, tokenType=Bearer, expiresAt=" + getExpiresAt() + "]";
    }
}
