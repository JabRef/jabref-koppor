package org.jabref.gui.help;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.stage.Stage;

import org.jabref.gui.DialogService;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.util.Version;
import org.jabref.preferences.InternalPreferences;
import org.jabref.testutils.category.GUITest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GUITest
@ExtendWith(ApplicationExtension.class)
class VersionWorkerTest {

    InternalPreferences internalPreferences;
    DialogService dialogService;
    VersionWorker versionWorker;

    @Start
    void onStart(Stage stage) {
        // Needed to init JavaFX thread
        stage.show();
    }

    @BeforeEach
    void setUp() {
        internalPreferences = mock(InternalPreferences.class);
        dialogService = mock(DialogService.class, Answers.RETURNS_DEEP_STUBS);
        versionWorker = new VersionWorker(Version.parse("1.0.0"),
                mock(DialogService.class, Answers.RETURNS_DEEP_STUBS),
                mock(TaskExecutor.class),
                internalPreferences);
    }

    @Test
    void getNewVersionTest() throws IOException {
        List<Version> availVersions = List.of(Version.parse("1.0.0"), Version.parse("2.0.0"));
        try (MockedStatic<Version> version = Mockito.mockStatic(Version.class)) {
            version.when(Version::getAllAvailableVersions)
                   .thenReturn(availVersions);

            assertEquals("2.0.0", versionWorker.getNewVersion().map(Version::toString).orElse(""));
        }
    }

    @Test
    void setIgnoredVersionTest() {
        Platform.runLater(() -> {
            versionWorker.showUpdateInfo(Optional.of(Version.parse("1.0.0")), true);

            when(dialogService.showCustomDialogAndWait(any())).thenReturn(Optional.of(false));

            verify(internalPreferences).setIgnoredVersion(eq(Version.parse("2.0.0")));
        });
    }
}
