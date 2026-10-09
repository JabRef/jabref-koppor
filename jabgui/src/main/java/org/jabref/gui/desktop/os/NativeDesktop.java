package org.jabref.gui.desktop.os;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import javafx.application.HostServices;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.gui.DialogService;
import org.jabref.gui.clipboard.ClipBoardManager;
import org.jabref.gui.externalfiletype.ExternalFileType;
import org.jabref.gui.externalfiletype.ExternalFileTypes;
import org.jabref.gui.frame.ExternalApplicationsPreferences;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.logic.FilePreferences;
import org.jabref.logic.importer.util.IdentifierParser;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.os.OS;
import org.jabref.logic.util.URLUtil;
import org.jabref.logic.util.io.FileUtil;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.identifier.DOI;
import org.jabref.model.entry.identifier.Identifier;

import com.airhacks.afterburner.injection.Injector;
import com.google.common.annotations.VisibleForTesting;
import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

import static org.jabref.model.entry.field.StandardField.PDF;
import static org.jabref.model.entry.field.StandardField.PS;
import static org.jabref.model.entry.field.StandardField.URL;

/// This class bundles OS specific implementations for file/application open handling methods.
/// In case the default does not work, subclasses provide the correct behavior.
///
/// We cannot use a static logger instance here in this class as the Logger first needs to be configured in the [JabKit#initLogging].
/// The configuration of tinylog will become immutable as soon as the first log entry is issued.
/// https://tinylog.org/v2/configuration/
///
/// AWT's [Desktop] is avoided where possible, as it is not available on all platforms; see
/// https://stackoverflow.com/questions/18004150/desktop-api-is-not-supported-on-the-current-platform.
/// Web links are opened via JavaFX [HostServices].
///
/// For non-GUI things, see [org.jabref.logic.os.OS].
@NullMarked
@AllowedToUseAwt("Because of moveToTrash() is not available elsewhere.")
public abstract class NativeDesktop {
    // No LOGGER may be initialized directly
    // Otherwise, org.jabref.Launcher.addLogToDisk will fail, because tinylog's properties are frozen

    private static final Pattern REMOTE_LINK_PATTERN = Pattern.compile("[a-z]+://.*");

    private final HostServices hostServices;

    protected NativeDesktop(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    /// Open a http/pdf/ps viewer for the given link string.
    ///
    /// Opening a PDF file at the file field is done at [org.jabref.gui.fieldeditors.LinkedFileViewModel#open]
    public static void openExternalViewer(BibDatabaseContext databaseContext,
                                          GuiPreferences preferences,
                                          String initialLink,
                                          Field initialField,
                                          DialogService dialogService,
                                          BibEntry entry)
            throws IOException {
        String link = initialLink;
        Field field = initialField;
        if ((PS == field) || (PDF == field)) {
            // Find the default directory for this field type:
            List<Path> directories = databaseContext.getFileDirectories(preferences.getFilePreferences());

            Optional<Path> file = FileUtil.find(link, directories);

            // Check that the file exists:
            if (file.isEmpty() || !Files.exists(file.get())) {
                throw new IOException("File not found (" + field + "): '" + link + "'.");
            }
            link = file.get().toAbsolutePath().toString();

            // Use the correct viewer even if pdf and ps are mixed up:
            String[] split = file.get().getFileName().toString().split("\\.");
            if (split.length >= 2) {
                if ("pdf".equalsIgnoreCase(split[split.length - 1])) {
                    field = PDF;
                } else if ("ps".equalsIgnoreCase(split[split.length - 1])
                        || ((split.length >= 3) && "ps".equalsIgnoreCase(split[split.length - 2]))) {
                    field = PS;
                }
            }
        } else if (StandardField.DOI == field) {
            openDoi(link, preferences);
            return;
        } else if (StandardField.ISBN == field) {
            openIsbn(link, preferences);
            return;
        } else if (StandardField.EPRINT == field) {
            IdentifierParser identifierParser = new IdentifierParser(entry);

            link = identifierParser.parse(StandardField.EPRINT)
                                   .flatMap(Identifier::getExternalURI)
                                   .map(URI::toASCIIString)
                                   .orElse(link);

            if (Objects.equals(link, initialLink)) {
                Optional<String> eprintTypeOpt = entry.getField(StandardField.EPRINTTYPE);
                Optional<String> archivePrefixOpt = entry.getField(StandardField.ARCHIVEPREFIX);
                if (eprintTypeOpt.isEmpty() && archivePrefixOpt.isEmpty()) {
                    dialogService.showErrorDialogAndWait(Localization.lang("Unable to open linked eprint. Please set the eprinttype field"));
                } else {
                    dialogService.showErrorDialogAndWait(Localization.lang("Unable to open linked eprint. Please verify that the eprint field has a valid '%0' id", link));
                }
            }
            // should be opened in browser
            field = URL;
        }

        switch (field) {
            case URL ->
                    openBrowser(link, preferences.getExternalApplicationsPreferences());
            case PS -> {
                try {
                    get().openFile(link, PS.getName(), preferences.getExternalApplicationsPreferences());
                } catch (IOException e) {
                    LoggerFactory.getLogger(NativeDesktop.class).error("An error occurred on the command: {}", link, e);
                }
            }
            case PDF -> {
                try {
                    get().openFile(link, PDF.getName(), preferences.getExternalApplicationsPreferences());
                } catch (IOException e) {
                    LoggerFactory.getLogger(NativeDesktop.class).error("An error occurred on the command: {}", link, e);
                }
            }
            case null,
                 default ->
                    LoggerFactory.getLogger(NativeDesktop.class).info("Message: currently only PDF, PS and HTML files can be opened by double clicking");
        }
    }

    private static void openDoi(String doi, GuiPreferences preferences) throws IOException {
        String link = DOI.parse(doi).map(DOI::getURIAsASCIIString).orElse(doi);
        openBrowser(link, preferences.getExternalApplicationsPreferences());
    }

    public static void openCustomDoi(String link, GuiPreferences preferences, DialogService dialogService) {
        DOI.parse(link)
           .flatMap(doi -> doi.getExternalURIWithCustomBase(preferences.getDOIPreferences().getDefaultBaseURI()))
           .ifPresent(uri -> {
               try {
                   openBrowser(uri, preferences.getExternalApplicationsPreferences());
               } catch (IOException e) {
                   dialogService.showErrorDialogAndWait(Localization.lang("Unable to open link."), e);
               }
           });
    }

    private static void openIsbn(String isbn, GuiPreferences preferences) throws IOException {
        String link = "https://openlibrary.org/isbn/" + isbn;
        openBrowser(link, preferences.getExternalApplicationsPreferences());
    }

    /// Open an external file, attempting to use the correct viewer for it.
    /// If the "file" is an online link, instead open it with the browser
    ///
    /// @param databaseContext The database this file belongs to.
    /// @param link            The filename.
    /// @return false if the link couldn't be resolved, true otherwise.
    public static boolean openExternalFileAnyFormat(final BibDatabaseContext databaseContext,
                                                    ExternalApplicationsPreferences externalApplicationsPreferences,
                                                    FilePreferences filePreferences,
                                                    String link,
                                                    final Optional<ExternalFileType> type) throws IOException {
        if (REMOTE_LINK_PATTERN.matcher(link.toLowerCase(Locale.ROOT)).matches()) {
            openBrowser(link, externalApplicationsPreferences);
            return true;
        }
        Optional<Path> file = FileUtil.find(databaseContext, link, filePreferences);
        if (file.isEmpty()) {
            return false;
        }

        get().openFileWithApplication(file.get().toString(), type.map(ExternalFileType::getOpenWithApplication).orElse(""));
        return true;
    }

    /// Opens a file browser of the folder of the given file. If possible, the file is selected
    ///
    /// @param fileLink the location of the file
    /// @throws IOException if the default file browser cannot be opened
    public static void openFolderAndSelectFile(Path fileLink,
                                               ExternalApplicationsPreferences externalApplicationsPreferences,
                                               DialogService dialogService) throws IOException {
        boolean useCustomFileBrowser = externalApplicationsPreferences.useCustomFileBrowser();
        if (!useCustomFileBrowser) {
            get().openFolderAndSelectFile(fileLink);
            return;
        }
        String absolutePath = fileLink.toAbsolutePath().getParent().toString();
        String command = externalApplicationsPreferences.getCustomFileBrowserCommand();
        if (command.isEmpty()) {
            LoggerFactory.getLogger(NativeDesktop.class).info("No custom file browser command defined");
            get().openFolderAndSelectFile(fileLink);
            return;
        }
        executeCommand(command, absolutePath, dialogService);
    }

    /// Opens a new console starting on the given file location
    ///
    /// @param file Location the console should be opened at.
    public static void openConsole(Path file, GuiPreferences preferences, DialogService dialogService) throws IOException {
        String absolutePath = file.toAbsolutePath().getParent().toString();

        boolean useCustomTerminal = preferences.getExternalApplicationsPreferences().useCustomTerminal();
        if (!useCustomTerminal) {
            get().openConsole(absolutePath, dialogService);
            return;
        }
        String command = preferences.getExternalApplicationsPreferences().getCustomTerminalCommand();
        command = command.trim();
        if (command.isEmpty()) {
            get().openConsole(absolutePath, dialogService);
            LoggerFactory.getLogger(NativeDesktop.class).info("Preference for custom terminal is empty. Using default terminal.");
            return;
        }
        executeCommand(command, absolutePath, dialogService);
    }

    private static void executeCommand(String command, String absolutePath, DialogService dialogService) {
        // normalize white spaces
        command = command.replaceAll("\\s+", " ");

        // replace the placeholder if used
        command = command.replace("%DIR", absolutePath);

        LoggerFactory.getLogger(NativeDesktop.class).info("Executing command \"{}\"...", command);
        dialogService.notify(Localization.lang("Executing command \"%0\"...", command));

        String[] subcommands = command.split(" ");
        try {
            new ProcessBuilder(subcommands).start();
        } catch (IOException exception) {
            LoggerFactory.getLogger(NativeDesktop.class).error("Error during command execution", exception);
            dialogService.notify(Localization.lang("Error occurred while executing the command \"%0\".", command));
        }
    }

    /// Opens the given URL using the system browser
    ///
    /// @param url the URL to open
    public static void openBrowser(String url, ExternalApplicationsPreferences externalApplicationsPreferences) throws IOException {
        openBrowser(url, externalApplicationsPreferences, get());
    }

    @VisibleForTesting
    static void openBrowser(String url, ExternalApplicationsPreferences externalApplicationsPreferences, NativeDesktop desktop) throws IOException {
        Optional<ExternalFileType> fileType = ExternalFileTypes.getExternalFileTypeByExt("html", externalApplicationsPreferences);
        if (fileType.isPresent() && !fileType.get().getOpenWithApplication().isEmpty()) {
            // The user configured a custom browser; hand it the URL string unmodified
            desktop.openFileWithApplication(url, fileType.get().getOpenWithApplication());
            return;
        }
        // A URL must be opened via a URL-aware API: the file-open path runs it through
        // Path.of(...), which collapses "https://" to "https:/" and yields a bogus filesystem path
        URI uri;
        try {
            uri = URLUtil.createUri(url);
        } catch (IllegalArgumentException e) {
            // Not URI-parseable (e.g. unencoded spaces); the OS URL handlers accept the raw string
            LoggerFactory.getLogger(NativeDesktop.class).debug("Could not parse {} as URI, falling back to the OS URL handler", url, e);
            desktop.openUrlWithSystemHandler(url);
            return;
        }
        // createUri also produces relative URIs, which only the platform opener can resolve
        if (uri.isAbsolute()) {
            desktop.showDocument(uri.toASCIIString());
        } else {
            desktop.openUrlWithSystemHandler(url);
        }
    }

    /// Not via [HostServices#showDocument], because on Windows that starts the web browser instead of the mail client.
    public static void openMailClient(URI mailto) throws IOException {
        get().openUrlWithSystemHandler(mailto.toASCIIString());
    }

    public static void openBrowser(URI url, ExternalApplicationsPreferences externalApplicationsPreferences) throws IOException {
        openBrowser(url.toASCIIString(), externalApplicationsPreferences);
    }

    /// Opens the url with the users standard Browser. If that fails a popup will be shown to instruct the user to open the link manually and the link gets copied to the clipboard
    ///
    /// @param url the URL to open
    public static void openBrowserShowPopup(String url, DialogService dialogService, ExternalApplicationsPreferences externalApplicationsPreferences) {
        try {
            openBrowser(url, externalApplicationsPreferences);
        } catch (IOException exception) {
            showManualOpenPopup(url, dialogService, exception);
        }
    }

    private static void showManualOpenPopup(String url, DialogService dialogService, IOException exception) {
        ClipBoardManager clipBoardManager = Injector.instantiateModelOrService(ClipBoardManager.class);
        clipBoardManager.setContent(url);
        LoggerFactory.getLogger(NativeDesktop.class).error("Could not open browser", exception);
        String couldNotOpenBrowser = Localization.lang("Could not open browser.");
        String openManually = Localization.lang("Please open %0 manually.", url);
        String copiedToClipboard = Localization.lang("The link has been copied to the clipboard.");
        dialogService.notify(couldNotOpenBrowser);
        dialogService.showErrorDialogAndWait(couldNotOpenBrowser, couldNotOpenBrowser + "\n" + openManually + "\n" + copiedToClipboard);
    }

    /// The instance created by [org.jabref.gui.JabRefGUI#start], for the static helpers of this class
    private static NativeDesktop get() {
        return Injector.instantiateModelOrService(NativeDesktop.class);
    }

    public static NativeDesktop create(HostServices hostServices) {
        if (OS.WINDOWS) {
            return new Windows(hostServices);
        } else if (OS.OS_X) {
            return new OSX(hostServices);
        } else if (OS.LINUX) {
            return new Linux(hostServices);
        }
        return new DefaultDesktop(hostServices);
    }

    public void openFile(String filePath, String fileType, ExternalApplicationsPreferences externalApplicationsPreferences) throws IOException {
        String application = ExternalFileTypes.getExternalFileTypeByExt(fileType, externalApplicationsPreferences)
                                              .map(ExternalFileType::getOpenWithApplication)
                                              .orElse("");
        openFileWithApplication(filePath, application);
    }

    /// Neither blocks nor reports failures, so callers cannot detect that no browser could be started.
    protected final void showDocument(String uri) {
        hostServices.showDocument(uri);
    }

    /// The OS handler dispatches by scheme, e.g., `mailto:` to the mail client.
    /// The URL must never be run through `Path.of`, which mangles it.
    public abstract void openUrlWithSystemHandler(String url) throws IOException;

    /// Opens a file on an Operating System, using the given application.
    ///
    /// @param filePath    The filename.
    /// @param application Link to the app that opens the file. If empty, the OS default application is used.
    public void openFileWithApplication(String filePath, String application) throws IOException {
        if (application.isEmpty()) {
            openFileWithDefaultApplication(filePath);
        } else {
            openFileWithCustomApplication(filePath, application);
        }
    }

    protected abstract void openFileWithDefaultApplication(String filePath) throws IOException;

    /// `application` is never empty.
    protected abstract void openFileWithCustomApplication(String filePath, String application) throws IOException;

    public abstract void openFolderAndSelectFile(Path file) throws IOException;

    public abstract void openConsole(String absolutePath, DialogService dialogService) throws IOException;

    /// Returns the path to the system's applications folder.
    ///
    /// @return the path
    public abstract Path getApplicationDirectory();

    /// Moves the given file to the trash.
    ///
    /// @throws UnsupportedOperationException if the current platform does not support the [Desktop.Action#MOVE_TO_TRASH] action
    /// @see Desktop#moveToTrash(java.io.File)
    public void moveToTrash(Path path) {
        boolean success = Desktop.getDesktop().moveToTrash(path.toFile());
        if (!success) {
            LoggerFactory.getLogger(NativeDesktop.class).warn("Could not move to trash. File {} is kept.", path);
        }
    }

    public boolean moveToTrashSupported() {
        return Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }
}
