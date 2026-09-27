package dev.portableagent.connection.crypto;

public class TokenCryptoException extends RuntimeException {
    public TokenCryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public TokenCryptoException(String message) {
        super(message);
    }
}
