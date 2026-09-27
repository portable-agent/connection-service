package dev.portableagent.connection.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portableagent.connection.model.Provider;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AesTokenCipherTest {
    private static final byte[] FIRST_KEY = new byte[32];
    private static final byte[] SECOND_KEY = new byte[32];

    static {
        FIRST_KEY[0] = 1;
        SECOND_KEY[0] = 2;
    }

    private final TokenOwner owner = new TokenOwner(UUID.randomUUID(), UUID.randomUUID(), Provider.GOOGLE_CALENDAR);

    @Test
    void encrypt_whenTokenIsValid_shouldDecryptSameToken() {
        var cipher = cipher(2, Map.of(1, FIRST_KEY, 2, SECOND_KEY));

        var encrypted = cipher.encrypt("refresh-token", owner);

        assertThat(encrypted.keyVersion()).isEqualTo(2);
        assertThat(cipher.decrypt(encrypted, owner)).isEqualTo("refresh-token");
    }

    @Test
    void encrypt_whenCalledTwice_shouldUseDifferentNonceAndCiphertext() {
        var cipher = cipher(1, Map.of(1, FIRST_KEY));

        var first = cipher.encrypt("refresh-token", owner);
        var second = cipher.encrypt("refresh-token", owner);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.data()).isNotEqualTo(second.data());
    }

    @Test
    void decrypt_whenTokenUsesPreviousKey_shouldUseStoredKeyVersion() {
        var oldCipher = cipher(1, Map.of(1, FIRST_KEY));
        var encrypted = oldCipher.encrypt("old-refresh-token", owner);
        var rotatedCipher = cipher(2, Map.of(1, FIRST_KEY, 2, SECOND_KEY));

        assertThat(rotatedCipher.decrypt(encrypted, owner)).isEqualTo("old-refresh-token");
    }

    @Test
    void decrypt_whenOwnerDiffers_shouldRejectMovedCiphertext() {
        var cipher = cipher(1, Map.of(1, FIRST_KEY));
        var encrypted = cipher.encrypt("refresh-token", owner);
        var otherOwner = new TokenOwner(owner.tenantId(), UUID.randomUUID(), owner.provider());

        assertThatThrownBy(() -> cipher.decrypt(encrypted, otherOwner))
                .isInstanceOf(TokenCryptoException.class)
                .hasMessage("Cannot decrypt token");
    }

    @Test
    void constructor_whenKeyIsNot256Bit_shouldRejectConfig() {
        assertThatThrownBy(() -> cipher(1, Map.of(1, new byte[16])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token key 1 must contain 32 bytes");
    }

    private AesTokenCipher cipher(int currentVersion, Map<Integer, byte[]> keys) {
        return new AesTokenCipher(currentVersion, keys, new SecureRandom());
    }
}
