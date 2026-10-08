package org.jabref.logic.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jabref.logic.ai.AiService;
import org.jabref.logic.os.OS;
import org.jabref.model.search.LinkedFilesConstants;

import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import com.sun.jna.platform.win32.ShlObj;
import com.sun.jna.platform.win32.Win32Exception;
import net.harawata.appdirs.AppDirsFactory;
import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

/// This collects all directories based on AppDirs.
/// OS-dependent directories for desktop integration are handled in the NativeDesktop class.
/// See e.g. `org.jabref.gui.desktop.os.NativeDesktop#getApplicationDirectory()`
///
/// No static logger instance may be used here, because the logger configuration must not be frozen before logging is initialized.
@NullMarked
public class Directories {
    /// Returns the path to the system's user directory.
    ///
    /// @return the path
    public static Path getUserDirectory() {
        return Path.of(System.getProperty("user.home"));
    }

    /// Returns the user's documents directory, falling back to the user directory if it cannot be determined.
    public static Path getDocumentsDirectory() {
        return DocumentsDirectoryHolder.DOCUMENTS_DIRECTORY;
    }

    /// Lazily determines the documents directory once, as the lookup may start an external process
    private static final class DocumentsDirectoryHolder {
        private static final Path DOCUMENTS_DIRECTORY = determineDocumentsDirectory();
    }

    private static Path determineDocumentsDirectory() {
        if (OS.WINDOWS) {
            return getWindowsDocumentsDirectory();
        }
        if (OS.LINUX) {
            return getLinuxDocumentsDirectory();
        }
        Path documents = getUserDirectory().resolve("Documents");
        return Files.exists(documents) ? documents : getUserDirectory();
    }

    private static Path getWindowsDocumentsDirectory() {
        try {
            try {
                return Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Documents));
            } catch (UnsatisfiedLinkError _) {
                // Windows Vista or earlier
                return Path.of(Shell32Util.getFolderPath(ShlObj.CSIDL_MYDOCUMENTS));
            }
        } catch (Win32Exception e) {
            LoggerFactory.getLogger(Directories.class).error("Error accessing folder", e);
            return getUserDirectory();
        }
    }

    private static Path getLinuxDocumentsDirectory() {
        String xdgDocumentsDir = System.getenv("XDG_DOCUMENTS_DIR");
        if (xdgDocumentsDir != null) {
            return Path.of(xdgDocumentsDir);
        }

        // Make use of xdg-user-dirs
        // See https://www.freedesktop.org/wiki/Software/xdg-user-dirs/ for details
        try {
            Process process = new ProcessBuilder("xdg-user-dir", "DOCUMENTS").start(); // Package name with 's', command without
            List<String> strings = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
                    .lines().toList();
            if (strings.isEmpty()) {
                LoggerFactory.getLogger(Directories.class).error("xdg-user-dir returned nothing");
                return getUserDirectory();
            }
            String documentsDirectory = strings.getFirst();
            Path documentsPath = Path.of(documentsDirectory);
            if (!Files.exists(documentsPath)) {
                LoggerFactory.getLogger(Directories.class).error("xdg-user-dir returned non-existent directory {}", documentsDirectory);
                return getUserDirectory();
            }
            LoggerFactory.getLogger(Directories.class).debug("Got documents path {}", documentsPath);
            return documentsPath;
        } catch (IOException e) {
            LoggerFactory.getLogger(Directories.class).error("Error while executing xdg-user-dir", e);
        }

        return getUserDirectory();
    }

    public static Path getLogDirectory(Version version) {
        return Path.of(AppDirsFactory.getInstance()
                                     .getUserDataDir(
                                             OS.APP_DIR_APP_NAME,
                                             "logs",
                                             OS.APP_DIR_APP_AUTHOR))
                   .resolve(version.toString());
    }

    public static Path getBackupDirectory() {
        return Path.of(AppDirsFactory.getInstance()
                                     .getUserDataDir(
                                             OS.APP_DIR_APP_NAME,
                                             "backups",
                                             OS.APP_DIR_APP_AUTHOR));
    }

    public static Path getFulltextIndexBaseDirectory() {
        return Path.of(AppDirsFactory.getInstance()
                                     .getUserDataDir(OS.APP_DIR_APP_NAME,
                                             "lucene" + File.separator + LinkedFilesConstants.VERSION,
                                             OS.APP_DIR_APP_AUTHOR));
    }

    public static Path getAiFilesDirectory() {
        return Path.of(AppDirsFactory.getInstance()
                                     .getUserDataDir(OS.APP_DIR_APP_NAME,
                                             "ai" + File.separator + AiService.VERSION,
                                             OS.APP_DIR_APP_AUTHOR));
    }

    /// Changes made while a shared database was unreachable, one file per database
    /// (see `org.jabref.logic.shared.OfflineChanges`)
    public static Path getSharedDatabaseDirectory() {
        return Path.of(AppDirsFactory.getInstance()
                                     .getUserDataDir(OS.APP_DIR_APP_NAME,
                                             "shared-database",
                                             OS.APP_DIR_APP_AUTHOR));
    }

    public static Path getSslDirectory() {
        return Path.of(AppDirsFactory.getInstance()
                                     .getUserDataDir(OS.APP_DIR_APP_NAME,
                                             "ssl",
                                             OS.APP_DIR_APP_AUTHOR));
    }

    public static Path getCitationsRelationsDirectory() {
        return Path.of(
                AppDirsFactory.getInstance()
                              .getUserDataDir(
                                      OS.APP_DIR_APP_NAME,
                                      "relations",
                                      OS.APP_DIR_APP_AUTHOR));
    }

    public static Path getCoverDirectory() {
        return Path.of(
                AppDirsFactory.getInstance()
                              .getUserDataDir(
                                      OS.APP_DIR_APP_NAME,
                                      "covers",
                                      OS.APP_DIR_APP_AUTHOR));
    }

    public static Path getMscDirectory() {
        return Path.of(
                AppDirsFactory.getInstance()
                              .getUserDataDir(
                                      OS.APP_DIR_APP_NAME,
                                      "msc",
                                      OS.APP_DIR_APP_AUTHOR));
    }
}
