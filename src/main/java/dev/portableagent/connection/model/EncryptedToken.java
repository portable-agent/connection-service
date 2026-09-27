package dev.portableagent.connection.model;

import java.util.Arrays;

public final class EncryptedToken {
    private final byte[] data;
    private final byte[] nonce;
    private final int keyVersion;

    public EncryptedToken(byte[] data, byte[] nonce, int keyVersion) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Encrypted token must not be empty");
        }
        if (nonce == null || nonce.length == 0) {
            throw new IllegalArgumentException("Token nonce must not be empty");
        }
        if (keyVersion < 1) {
            throw new IllegalArgumentException("Key version must be positive");
        }
        this.data = data.clone();
        this.nonce = nonce.clone();
        this.keyVersion = keyVersion;
    }

    public byte[] data() {
        return data.clone();
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public int keyVersion() {
        return keyVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EncryptedToken token)) {
            return false;
        }
        return keyVersion == token.keyVersion && Arrays.equals(data, token.data) && Arrays.equals(nonce, token.nonce);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(data);
        result = 31 * result + Arrays.hashCode(nonce);
        return 31 * result + keyVersion;
    }
}
