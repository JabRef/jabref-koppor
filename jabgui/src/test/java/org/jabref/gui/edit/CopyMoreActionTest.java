package org.jabref.gui.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jabref.gui.ClipBoardManager;
import org.jabref.gui.DialogService;
import org.jabref.gui.JabRefDialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.StandardActions;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.push.CitationCommandString;
import org.jabref.logic.push.PushToApplicationPreferences;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopyMoreActionTest {

    private final DialogService dialogService = spy(DialogService.class);
    private final ClipBoardManager clipBoardManager = mock(ClipBoardManager.class);
    private final GuiPreferences preferences = mock(GuiPreferences.class);
    private final JournalAbbreviationRepository abbreviationRepository = mock(JournalAbbreviationRepository.class);
    private final StateManager stateManager = mock(StateManager.class);
    private final List<String> titles = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final List<String> dois = new ArrayList<>();

    private CopyMoreAction copyMoreAction;
    private BibEntry entry;

    @BeforeEach
    void setUp() {
        String title = "A tale from the trenches";
        entry = new BibEntry(StandardEntryType.Misc)
                .withField(StandardField.AUTHOR, "Souti Chattopadhyay and Nicholas Nelson and Audrey Au and Natalia Morales and Christopher Sanchez and Rahul Pandita and Anita Sarma")
                .withField(StandardField.TITLE, title)
                .withField(StandardField.YEAR, "2020")
                .withField(StandardField.DOI, "10.1145/3377811.3380330")
                .withField(StandardField.SUBTITLE, "cognitive biases and software development")
                .withCitationKey("abc");
        titles.add(title);
        keys.add("abc");
        dois.add("10.1145/3377811.3380330");

        PushToApplicationPreferences pushToApplicationPreferences = mock(PushToApplicationPreferences.class);
        when(pushToApplicationPreferences.getCiteCommand()).thenReturn(new CitationCommandString("\\cite{", ",", "}"));
        when(preferences.getPushToApplicationPreferences()).thenReturn(pushToApplicationPreferences);
    }

    @Test
    void executeOnFail() {
        when(stateManager.getActiveDatabase()).thenReturn(Optional.empty());
        when(stateManager.getSelectedEntries()).thenReturn(FXCollections.emptyObservableList());
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_TITLE, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        verify(clipBoardManager, times(0)).setContent(any(String.class));
        verify(dialogService, times(0)).notify(any(String.class));
    }

    @Test
    void executeCopyTitleWithNoTitle() {
        BibEntry entryWithNoTitle = new BibEntry(entry);
        entryWithNoTitle.clearField(StandardField.TITLE);
        ObservableList<BibEntry> entriesWithNoTitles = FXCollections.observableArrayList(entryWithNoTitle);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(entriesWithNoTitles));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(entriesWithNoTitles);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_TITLE, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        verify(clipBoardManager, times(0)).setContent(any(String.class));
        verify(dialogService, times(1)).notify(Localization.lang("None of the selected entries have titles."));
    }

    @Test
    void executeCopyTitleOnPartialSuccess() {
        BibEntry entryWithNoTitle = new BibEntry(entry);
        entryWithNoTitle.clearField(StandardField.TITLE);
        ObservableList<BibEntry> mixedEntries = FXCollections.observableArrayList(entryWithNoTitle, entry);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(mixedEntries));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(mixedEntries);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_TITLE, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        String copiedTitles = String.join("\n", titles);
        verify(clipBoardManager, times(1)).setContent(copiedTitles);
        verify(dialogService, times(1)).notify(Localization.lang("Warning: %0 out of %1 entries have undefined title.",
                Integer.toString(mixedEntries.size() - titles.size()), Integer.toString(mixedEntries.size())));
    }

    @Test
    void executeCopyTitleOnSuccess() {
        ObservableList<BibEntry> entriesWithTitles = FXCollections.observableArrayList(entry);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(entriesWithTitles));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(entriesWithTitles);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_TITLE, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        String copiedTitles = String.join("\n", titles);
        verify(clipBoardManager, times(1)).setContent(copiedTitles);
        verify(dialogService, times(1)).notify(Localization.lang("Copied '%0' to clipboard.",
                JabRefDialogService.shortenDialogMessage(copiedTitles)));
    }

    @Test
    void executeCopyKeyWithNoKey() {
        BibEntry entryWithNoKey = new BibEntry(entry);
        entryWithNoKey.clearCiteKey();
        ObservableList<BibEntry> entriesWithNoKeys = FXCollections.observableArrayList(entryWithNoKey);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(entriesWithNoKeys));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(entriesWithNoKeys);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_KEY, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        verify(clipBoardManager, times(0)).setContent(any(String.class));
        verify(dialogService, times(1)).notify(Localization.lang("None of the selected entries have citation keys."));
    }

    @Test
    void executeCopyKeyOnPartialSuccess() {
        BibEntry entryWithNoKey = new BibEntry(entry);
        entryWithNoKey.clearCiteKey();
        ObservableList<BibEntry> mixedEntries = FXCollections.observableArrayList(entryWithNoKey, entry);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(mixedEntries));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(mixedEntries);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_KEY, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        String copiedKeys = String.join("\n", keys);
        verify(clipBoardManager, times(1)).setContent(copiedKeys);
        verify(dialogService, times(1)).notify(Localization.lang("Warning: %0 out of %1 entries have undefined citation key.",
                Integer.toString(mixedEntries.size() - titles.size()), Integer.toString(mixedEntries.size())));
    }

    @Test
    void executeCopyKeyOnSuccess() {
        ObservableList<BibEntry> entriesWithKeys = FXCollections.observableArrayList(entry);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(entriesWithKeys));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(entriesWithKeys);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_KEY, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        String copiedKeys = String.join("\n", keys);
        verify(clipBoardManager, times(1)).setContent(copiedKeys);
        verify(dialogService, times(1)).notify(Localization.lang("Copied '%0' to clipboard.",
                JabRefDialogService.shortenDialogMessage(copiedKeys)));
    }

    @Test
    void executeCopyDoiWithNoDoi() {
        BibEntry entryWithNoDoi = new BibEntry(entry);
        entryWithNoDoi.clearField(StandardField.DOI);
        ObservableList<BibEntry> entriesWithNoDois = FXCollections.observableArrayList(entryWithNoDoi);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(entriesWithNoDois));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(entriesWithNoDois);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_DOI, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        verify(clipBoardManager, times(0)).setContent(any(String.class));
        verify(dialogService, times(1)).notify(Localization.lang("None of the selected entries have DOIs."));
    }

    @Test
    void executeCopyDoiOnPartialSuccess() {
        BibEntry entryWithNoDoi = new BibEntry(entry);
        entryWithNoDoi.clearField(StandardField.DOI);
        ObservableList<BibEntry> mixedEntries = FXCollections.observableArrayList(entryWithNoDoi, entry);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(mixedEntries));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(mixedEntries);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_DOI, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        String copiedDois = String.join("\n", dois);
        verify(clipBoardManager, times(1)).setContent(copiedDois);
        verify(dialogService, times(1)).notify(Localization.lang("Warning: %0 out of %1 entries have undefined DOIs.",
                Integer.toString(mixedEntries.size() - titles.size()), Integer.toString(mixedEntries.size())));
    }

    @Test
    void executeCopyDoiOnSuccess() {
        ObservableList<BibEntry> entriesWithDois = FXCollections.observableArrayList(entry);
        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase(entriesWithDois));

        when(stateManager.getActiveDatabase()).thenReturn(Optional.ofNullable(databaseContext));
        when(stateManager.getSelectedEntries()).thenReturn(entriesWithDois);
        copyMoreAction = new CopyMoreAction(StandardActions.COPY_DOI, dialogService, stateManager, clipBoardManager, preferences, abbreviationRepository);
        copyMoreAction.execute();

        String copiedDois = String.join("\n", dois);
        verify(clipBoardManager, times(1)).setContent(copiedDois);
        verify(dialogService, times(1)).notify(Localization.lang("Copied '%0' to clipboard.",
                JabRefDialogService.shortenDialogMessage(copiedDois)));
    }
}
