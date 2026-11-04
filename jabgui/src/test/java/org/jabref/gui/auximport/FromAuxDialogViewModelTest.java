package org.jabref.gui.auximport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FromAuxDialogViewModelTest {

    @Test
    void findsNewestAuxNextToTexFile(@TempDir Path directory) throws Exception {
        Files.createFile(directory.resolve("paper.tex"));
        Path paperAux = Files.createFile(directory.resolve("paper.aux"));
        Files.createFile(directory.resolve("chapter.tex"));
        Path chapterAux = Files.createFile(directory.resolve("chapter.aux"));
        Files.createFile(directory.resolve("orphan.aux"));
        Files.setLastModifiedTime(chapterAux, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(paperAux, FileTime.fromMillis(2_000));

        assertEquals(Optional.of(paperAux), FromAuxDialogViewModel.findAuxFile(directory));
    }

    @Test
    void ignoresAuxWithoutTexFile(@TempDir Path directory) throws Exception {
        Files.createFile(directory.resolve("orphan.aux"));

        assertEquals(Optional.empty(), FromAuxDialogViewModel.findAuxFile(directory));
    }

    @Test
    void missingDirectoryYieldsNothing(@TempDir Path directory) {
        assertEquals(Optional.empty(), FromAuxDialogViewModel.findAuxFile(directory.resolve("missing")));
    }
}
