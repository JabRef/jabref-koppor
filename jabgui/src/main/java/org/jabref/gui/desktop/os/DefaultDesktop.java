package org.jabref.gui.desktop.os;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javafx.application.HostServices;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.gui.DialogService;
import org.jabref.logic.util.Directories;

import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

/// This class contains some default implementations (if OS is neither linux, windows or osx) file directories and file/application open handling methods.
///
/// We cannot use a static logger instance here in this class as the Logger first needs to be configured in the [JabKit#initLogging].
/// The configuration of tinylog will become immutable as soon as the first log entry is issued.
/// https://tinylog.org/v2/configuration/
@NullMarked
@AllowedToUseAwt("Requires AWT to open a file")
public class DefaultDesktop extends NativeDesktop {

    public DefaultDesktop(HostServices hostServices) {
        super(hostServices);
    }

    @Override
    protected void openFileWithDefaultApplication(String filePath) throws IOException {
        Desktop.getDesktop().open(Path.of(filePath).toFile());
    }

    @Override
    public void openUrlWithSystemHandler(String url) {
        // No native URL handler known for this platform
        showDocument(url);
    }

    @Override
    protected void openFileWithCustomApplication(String filePath, String application) throws IOException {
        new ProcessBuilder(application, filePath).start();
    }

    @Override
    public void openFolderAndSelectFile(Path filePath) throws IOException {
        File file = filePath.toAbsolutePath().getParent().toFile();
        Desktop.getDesktop().open(file);
    }

    @Override
    public void openConsole(String absolutePath, DialogService dialogService) throws IOException {
        LoggerFactory.getLogger(DefaultDesktop.class).error("This feature is not supported by your Operating System.");
    }

    @Override
    public Path getApplicationDirectory() {
        return Directories.getUserDirectory();
    }
}
