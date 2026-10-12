package org.jabref.gui.util;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.stage.FileChooser.ExtensionFilter;
import org.jabref.gui.util.FileDialogConfiguration.Builder;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.FileType;
import org.jabref.logic.util.StandardFileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileDialogConfigurationTest {

    @Test
    void withValidDirectoryString(@TempDir Path folder) {
        String tempFolder = folder.toAbsolutePath().toString();

        FileDialogConfiguration fileDialogConfiguration = new Builder()
                .withInitialDirectory(tempFolder)
                .build();

        assertEquals(Optional.of(Path.of(tempFolder)), fileDialogConfiguration.getInitialDirectory());
    }

    @Test
    void withValidDirectoryPath(@TempDir Path tempFolder) {
        FileDialogConfiguration fileDialogConfiguration = new Builder()
                .withInitialDirectory(tempFolder)
                .build();

        assertEquals(Optional.of(tempFolder), fileDialogConfiguration.getInitialDirectory());
    }

    @Test
    void withNullStringDirectory() {
        FileDialogConfiguration fileDialogConfiguration = new Builder()
                .withInitialDirectory((String) null)
                .build();

        assertEquals(Optional.empty(), fileDialogConfiguration.getInitialDirectory());
    }

    @Test
    void withNullPathDirectory() {
        FileDialogConfiguration fileDialogConfiguration = new Builder()
                .withInitialDirectory((Path) null)
                .build();

        assertEquals(Optional.empty(), fileDialogConfiguration.getInitialDirectory());
    }

    @Test
    void withNonExistingDirectoryAndParentNull() {
        String tempFolder = "workingDirectory";
        FileDialogConfiguration fileDialogConfiguration = new Builder()
                .withInitialDirectory(tempFolder)
                .build();

        assertEquals(Optional.empty(), fileDialogConfiguration.getInitialDirectory());
    }

    @Test
    void singleExtension() {
        FileDialogConfiguration fileDialogConfiguration = new Builder()
                .withDefaultExtension(StandardFileType.BIBTEX_DB)
                .build();

        ExtensionFilter filter = toFilter("%1s %2s".formatted("BibTex", Localization.lang("Library")), StandardFileType.BIBTEX_DB);

        assertEquals(filter.getExtensions(),
                fileDialogConfiguration.getDefaultExtension().getExtensions());
    }

    private ExtensionFilter toFilter(String description, FileType extension) {
        return new ExtensionFilter(description,
                extension.getExtensions()
                         .stream()
                         .map(ending -> "*." + ending)
                         .collect(Collectors.toList()));
    }
}
