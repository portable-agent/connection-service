package dev.portableagent.connection.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EncryptedTokenTest {
    @Test
    void constructor_whenSourceArraysChange_shouldKeepOwnCopy() {
        var data = new byte[] {1, 2, 3};
        var nonce = new byte[] {4, 5, 6};
        var token = new EncryptedToken(data, nonce, 1);

        data[0] = 9;
        nonce[0] = 9;

        assertThat(token.data()).containsExactly(1, 2, 3);
        assertThat(token.nonce()).containsExactly(4, 5, 6);
    }

    @Test
    void accessors_whenReturnedArraysChange_shouldKeepOwnCopy() {
        var token = new EncryptedToken(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, 1);

        token.data()[0] = 9;
        token.nonce()[0] = 9;

        assertThat(token.data()).containsExactly(1, 2, 3);
        assertThat(token.nonce()).containsExactly(4, 5, 6);
    }

    @Test
    void constructor_whenEncryptedDataIsMissing_shouldRejectIt() {
        assertThatThrownBy(() -> new EncryptedToken(new byte[0], new byte[] {1}, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Encrypted token must not be empty");
    }
}
