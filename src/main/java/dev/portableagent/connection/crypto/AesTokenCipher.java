package dev.portableagent.connection.crypto;

import dev.portableagent.connection.model.EncryptedToken;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesTokenCipher implements TokenCipher {
    private static final String CIPHER_NAME = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final int currentVersion;
    private final Map<Integer, SecretKeySpec> keys;
    private final SecureRandom random;

    public AesTokenCipher(int currentVersion, Map<Integer, byte[]> keys, SecureRandom random) {
        if (currentVersion < 1) {
            throw new IllegalArgumentException("Current key version must be positive");
        }
        Objects.requireNonNull(keys);
        this.random = Objects.requireNonNull(random);
        this.keys = copyKeys(keys);
        if (!this.keys.containsKey(currentVersion)) {
            throw new IllegalArgumentException("Current token key is missing");
        }
        this.currentVersion = currentVersion;
    }

    @Override
    public EncryptedToken encrypt(String token, TokenOwner owner) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }
        Objects.requireNonNull(owner);
        var nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            var cipher = cipher(Cipher.ENCRYPT_MODE, keys.get(currentVersion), nonce, owner);
            var data = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return new EncryptedToken(data, nonce, currentVersion);
        } catch (GeneralSecurityException error) {
            throw new TokenCryptoException("Cannot encrypt token", error);
        }
    }

    @Override
    public String decrypt(EncryptedToken token, TokenOwner owner) {
        Objects.requireNonNull(token);
        Objects.requireNonNull(owner);
        var key = keys.get(token.keyVersion());
        if (key == null) {
            throw new TokenCryptoException("Cannot decrypt token");
        }
        try {
            var cipher = cipher(Cipher.DECRYPT_MODE, key, token.nonce(), owner);
            return new String(cipher.doFinal(token.data()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException error) {
            throw new TokenCryptoException("Cannot decrypt token", error);
        }
    }

    private Cipher cipher(int mode, SecretKeySpec key, byte[] nonce, TokenOwner owner) throws GeneralSecurityException {
        var cipher = Cipher.getInstance(CIPHER_NAME);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(ownerText(owner).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private String ownerText(TokenOwner owner) {
        return owner.tenantId() + "|" + owner.actorId() + "|" + owner.provider().value();
    }

    private Map<Integer, SecretKeySpec> copyKeys(Map<Integer, byte[]> source) {
        var result = new HashMap<Integer, SecretKeySpec>();
        source.forEach((version, value) -> {
            if (version == null || version < 1) {
                throw new IllegalArgumentException("Token key version must be positive");
            }
            if (value == null || value.length != KEY_BYTES) {
                throw new IllegalArgumentException("Token key " + version + " must contain 32 bytes");
            }
            result.put(version, new SecretKeySpec(value.clone(), "AES"));
        });
        return Map.copyOf(result);
    }
}
