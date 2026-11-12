package org.jabref.logic.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.oauth2.sdk.token.RefreshToken;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
// [utest->req~citedrive.credentials.server~1]
class CiteDriveTokenStorageTest {

    @Test
    void storedTokenCanOnlyBeLoadedForItsOriginalServer() {
        RefreshToken token = new RefreshToken("stored");
        String json = JabRefCliPreferences.serializeCiteDriveToken(token, "https://original.example/");

        assertEquals(token, JabRefCliPreferences.parseCiteDriveToken(json, "https://original.example/"));
        assertEquals(
                Optional.empty(),
                Optional.ofNullable(JabRefCliPreferences.parseCiteDriveToken(json, "https://other.example/")));
    }

    @Test
    void legacyTokenWithoutServerBindingRequiresAnotherLogin() {
        String json = new RefreshToken("legacy").toJSONObject().toJSONString();

        assertEquals(
                Optional.empty(),
                Optional.ofNullable(JabRefCliPreferences.parseCiteDriveToken(json, "https://original.example/")));
    }
}
