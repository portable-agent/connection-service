package dev.portableagent.connection.service;

import dev.portableagent.connection.crypto.TokenOwner;

public record CompletedOAuthSession(TokenOwner owner, String verifier) {
    @Override
    public String toString() {
        return "CompletedOAuthSession[owner=" + owner + ", verifier=<redacted>]";
    }
}
