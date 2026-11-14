package org.jabref.logic.shared.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PasswordTest {

    @Test
    void decryptsLegacyCiphertextFromCharArray() throws Exception {
        char[] charPhrase = "xvrYKSvZj1U33vnvKk6b/A==".toCharArray();
        Password charPassword = new Password(charPhrase, "someKey");
        String charDecryptedPassword = charPassword.decrypt();
        assertEquals("Password", charDecryptedPassword);
    }

    @Test
    void decryptsLegacyCiphertext() throws Exception {
        Password password = new Password("xvrYKSvZj1U33vnvKk6b/A==", "someKey");
        assertEquals("Password", password.decrypt());
    }
}
