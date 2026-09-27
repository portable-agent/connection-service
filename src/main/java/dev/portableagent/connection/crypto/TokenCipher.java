package dev.portableagent.connection.crypto;

import dev.portableagent.connection.model.EncryptedToken;

public interface TokenCipher {
    EncryptedToken encrypt(String token, TokenOwner owner);

    String decrypt(EncryptedToken token, TokenOwner owner);
}
