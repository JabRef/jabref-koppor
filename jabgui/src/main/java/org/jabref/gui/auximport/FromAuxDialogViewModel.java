package org.jabref.gui.auximport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;

import org.jabref.gui.DialogService;
import org.jabref.gui.LibraryTabContainer;
import org.jabref.gui.StateManager;
import org.jabref.gui.util.FileDialogConfiguration;
import org.jabref.logic.auxparser.AuxParser;
import org.jabref.logic.auxparser.AuxParserResult;
import org.jabref.logic.auxparser.AuxParserStatisticsProvider;
import org.jabref.logic.auxparser.DefaultAuxParser;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.shared.DatabaseLocation;
import org.jabref.logic.util.StandardFileType;
import org.jabref.logic.util.io.FileUtil;
import org.jabref.logic.util.strings.StringUtil;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;

import com.tobiasdiez.easybind.EasyBind;

public class FromAuxDialogViewModel {

    // Initially true: nothing has been parsed yet, so there is no result to generate a library from
    private final BooleanProperty parseFailedProperty = new SimpleBooleanProperty(true);
    private final StringProperty auxFileProperty = new SimpleStringProperty();
    private final StringProperty statusTextProperty = new SimpleStringProperty();
    private final ListProperty<String> notFoundList = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<BibDatabaseContext> librariesProperty = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ObjectProperty<BibDatabaseContext> selectedLibraryProperty = new SimpleObjectProperty<>();

    private final LibraryTabContainer tabContainer;
    private final DialogService dialogService;
    private final CliPreferences preferences;
    private final StateManager stateManager;

    private AuxParserResult auxParserResult;

    public FromAuxDialogViewModel(LibraryTabContainer tabContainer,
                                  DialogService dialogService,
                                  CliPreferences preferences,
                                  StateManager stateManager) {
        this.tabContainer = tabContainer;
        this.dialogService = dialogService;
        this.preferences = preferences;
        this.stateManager = stateManager;

        librariesProperty.setAll(stateManager.getOpenDatabases());
        selectedLibraryProperty.set(tabContainer.getCurrentLibraryTab().getBibDatabaseContext());
        prefillAuxFile();
        EasyBind.listen(selectedLibraryProperty, (_, _, _) -> {
            if (StringUtil.isBlank(auxFileProperty.get())) {
                prefillAuxFile();
            }
            if (auxParserResult != null) {
                parse();
            }
        });
        // A changed AUX file path invalidates the previous parse result
        EasyBind.listen(auxFileProperty, (_, _, _) -> {
            auxParserResult = null;
            parseFailedProperty.set(true);
        });
    }

    /// Fills in the AUX file LaTeX wrote next to the library's .tex files, i.e., in the directory the LaTeX citations tab scans.
    private void prefillAuxFile() {
        BibDatabaseContext context = selectedLibraryProperty.get();
        Path directory = context.getMetaData().getLatexFileDirectory(preferences.getFilePreferences().getUserAndHost())
                                .orElse(FileUtil.getInitialDirectory(context, preferences.getFilePreferences().getWorkingDirectory()));
        findAuxFile(directory).ifPresent(aux -> auxFileProperty.set(aux.toAbsolutePath().toString()));
    }

    /// Returns the AUX file belonging to a .tex file in the given directory.
    /// With `\include`, LaTeX writes one AUX file per included file; the main one is closed last, thus the newest is taken.
    // [impl->req~import.aux.prefill~1]
    static Optional<Path> findAuxFile(Path directory) {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> FileUtil.getFileExtension(file).filter("aux"::equalsIgnoreCase).isPresent())
                        .filter(aux -> Files.exists(aux.resolveSibling(FileUtil.getBaseName(aux) + ".tex")))
                        .max(Comparator.comparing(FromAuxDialogViewModel::lastModified));
        } catch (IOException | UncheckedIOException _) {
            return Optional.empty();
        }
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String getDatabaseName(BibDatabaseContext databaseContext) {
        Optional<String> dbOpt = Optional.empty();
        if (databaseContext.getDatabasePath().isPresent()) {
            dbOpt = FileUtil.getUniquePathFragment(stateManager.getAllDatabasePaths(), databaseContext.getDatabasePath().get());
        }
        if (databaseContext.getLocation() == DatabaseLocation.SHARED) {
            return databaseContext.getDBMSSynchronizer().getDBName() + " [" + Localization.lang("shared") + "]";
        }

        return dbOpt.orElse(Localization.lang("untitled"));
    }

    public void browse() {
        FileDialogConfiguration fileDialogConfiguration = new FileDialogConfiguration.Builder()
                .addExtensionFilter(StandardFileType.AUX)
                .withDefaultExtension(StandardFileType.AUX)
                .withInitialDirectory(auxFileProperty.get() == null ? preferences.getFilePreferences().getWorkingDirectory().toString() : auxFileProperty.get()).build();
        dialogService.showFileOpenDialog(fileDialogConfiguration).ifPresent(file -> auxFileProperty.setValue(file.toAbsolutePath().toString()));
    }

    public void parse() {
        parseFailedProperty.set(false);
        auxParserResult = null;
        notFoundList.clear();
        statusTextProperty.setValue("");
        BibDatabase referenceDatabase = selectedLibraryProperty.get().getDatabase();
        String auxName = auxFileProperty.get();

        if ((auxName != null) && (referenceDatabase != null) && !auxName.isEmpty()) {
            AuxParser auxParser = new DefaultAuxParser(referenceDatabase);
            auxParserResult = auxParser.parse(Path.of(auxName));
            notFoundList.setAll(auxParserResult.getUnresolvedKeys());
            statusTextProperty.set(new AuxParserStatisticsProvider(auxParserResult).getInformation(false));

            if (!auxParserResult.getGeneratedBibDatabase().hasEntries()) {
                // The generated database contains no entries -> no active generate-button
                statusTextProperty.set(statusTextProperty.get() + "\n" + Localization.lang("empty library"));
                parseFailedProperty.set(true);
            }
        } else {
            parseFailedProperty.set(true);
        }
    }

    public void addResultToTabContainer() {
        if (auxParserResult == null) {
            return;
        }
        BibDatabaseContext context = new BibDatabaseContext(auxParserResult.getGeneratedBibDatabase());
        tabContainer.addTab(context, true);
    }

    public BooleanProperty parseFailedProperty() {
        return parseFailedProperty;
    }

    public StringProperty auxFileProperty() {
        return auxFileProperty;
    }

    public StringProperty statusTextProperty() {
        return statusTextProperty;
    }

    public ReadOnlyListProperty<String> notFoundList() {
        return notFoundList;
    }

    public ReadOnlyListProperty<BibDatabaseContext> librariesProperty() {
        return librariesProperty;
    }

    public ObjectProperty<BibDatabaseContext> selectedLibraryProperty() {
        return selectedLibraryProperty;
    }
}
