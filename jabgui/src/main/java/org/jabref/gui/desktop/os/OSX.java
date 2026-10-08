package org.jabref.gui.desktop.os;

import java.io.IOException;
import java.nio.file.Path;

import javafx.application.HostServices;

import org.jabref.gui.DialogService;

import org.jspecify.annotations.NullMarked;

/// This class contains macOS (OSX) specific implementations for file directories and file/application open handling methods.
///
/// We cannot use a static logger instance here in this class as the Logger first needs to be configured in the [JabKit#initLogging].
/// The configuration of tinylog will become immutable as soon as the first log entry is issued.
/// https://tinylog.org/v2/configuration/
@NullMarked
public class OSX extends NativeDesktop {

    public OSX(HostServices hostServices) {
        super(hostServices);
    }

    @Override
    protected void openFileWithDefaultApplication(String filePath) throws IOException {
        new ProcessBuilder("/usr/bin/open", filePath).start();
    }

    @Override
    public void openUrlWithSystemHandler(String url) throws IOException {
        new ProcessBuilder("/usr/bin/open", url).start();
    }

    @Override
    protected void openFileWithCustomApplication(String filePath, String application) throws IOException {
        new ProcessBuilder("/usr/bin/open", "-a", application, filePath).start();
    }

    @Override
    public void openFolderAndSelectFile(Path file) throws IOException {
        new ProcessBuilder("/usr/bin/open", "-R", file.toString()).start();
    }

    @Override
    public void openConsole(String absolutePath, DialogService dialogService) throws IOException {
        new ProcessBuilder("open", "-a", "Terminal", absolutePath).start();
    }

    @Override
    public Path getApplicationDirectory() {
        return Path.of("/Applications");
    }
}
