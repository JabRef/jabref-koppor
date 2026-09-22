package org.jabref.gui.externalfiles;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.jabref.gui.DialogService;
import org.jabref.gui.JabRefGuiStateManager;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.util.UiTaskExecutor;
import org.jabref.logic.importer.FetcherResult;
import org.jabref.logic.importer.fetcher.TrustLevel;
import org.jabref.logic.util.BackgroundTask;
import org.jabref.logic.util.URLUtil;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class DownloadFullTextActionTest {

    private DialogService dialogService;
    private JabRefGuiStateManager stateManager;
    private GuiPreferences preferences;
    private UiTaskExecutor taskExecutor;
    private BibDatabaseContext databaseContext;
    private BibEntry entry;
    private FetcherResult fetcherResult;
    private List<Runnable> pendingUiActions;

    @BeforeEach
    void setUp() throws MalformedURLException {
        pendingUiActions = new ArrayList<>();
        dialogService = mock(DialogService.class);
        stateManager = new JabRefGuiStateManager();
        preferences = mock(GuiPreferences.class);
        taskExecutor = mock(UiTaskExecutor.class);
        when(taskExecutor.execute(any(BackgroundTask.class))).thenReturn(CompletableFuture.completedFuture(null));

        databaseContext = new BibDatabaseContext();
        stateManager.getOpenDatabases().add(databaseContext);
        stateManager.setActiveDatabase(databaseContext);

        entry = new BibEntry()
                .withField(StandardField.TITLE, "Original title")
                .withField(StandardField.DOI, "10.1000/original");
        databaseContext.getDatabase().insertEntry(entry);
        stateManager.setSelectedEntries(List.of(entry));

        fetcherResult = new FetcherResult(TrustLevel.PUBLISHER, URLUtil.create("https://example.org/test.pdf"), Map.of());
    }

    @Test
    void downloadsForUnchangedEntry() throws Exception {
        RecordingDownloadFullTextAction action = new RecordingDownloadFullTextAction(_ -> Optional.of(fetcherResult));

        BackgroundTask<?> task = captureTask(action);
        completeTask(task);

        assertEquals(List.of(entry), action.downloadedEntries);
        assertEquals(List.of(fetcherResult), action.downloadedResults);
    }

    @Test
    void skipsDownloadWhenEntryChangedAfterLookup() throws Exception {
        RecordingDownloadFullTextAction action = new RecordingDownloadFullTextAction(_ -> Optional.of(fetcherResult));

        BackgroundTask<?> task = captureTask(action);
        task.call();
        entry.withField(StandardField.TITLE, "Updated title");
        runPendingUiActions();

        assertEquals(List.of(), action.downloadedEntries);
    }

    @Test
    void skipsDownloadWhenEntryDeletedAfterLookup() throws Exception {
        RecordingDownloadFullTextAction action = new RecordingDownloadFullTextAction(_ -> Optional.of(fetcherResult));

        BackgroundTask<?> task = captureTask(action);
        task.call();
        databaseContext.getDatabase().removeEntry(entry);
        runPendingUiActions();

        assertEquals(List.of(), action.downloadedEntries);
    }

    @Test
    void finderReceivesEntrySnapshot() throws Exception {
        RecordingDownloadFullTextAction action = new RecordingDownloadFullTextAction(snapshot -> {
            assertNotSame(entry, snapshot);
            assertEquals(Optional.of("Original title"), snapshot.getField(StandardField.TITLE));
            return Optional.of(fetcherResult);
        });

        BackgroundTask<?> task = captureTask(action);
        completeTask(task);

        assertEquals(List.of(entry), action.downloadedEntries);
    }

    @Test
    void attachesEachEntryBeforeLookingUpTheNext() throws Exception {
        BibEntry secondEntry = new BibEntry().withField(StandardField.DOI, "10.1000/second");
        databaseContext.getDatabase().insertEntry(secondEntry);
        stateManager.setSelectedEntries(List.of(entry, secondEntry));
        List<BibEntry> attachedEntries = new ArrayList<>();
        List<Integer> attachedBeforeLookup = new ArrayList<>();
        DownloadFullTextAction action = new DownloadFullTextAction(dialogService, stateManager, preferences, taskExecutor, _ -> {
            runPendingUiActions();
            attachedBeforeLookup.add(attachedEntries.size());
            return Optional.of(fetcherResult);
        }, pendingUiActions::add) {
            @Override
            void addLinkedFileFromURL(BibDatabaseContext databaseContext, FetcherResult result, BibEntry entry) {
                attachedEntries.add(entry);
            }
        };

        BackgroundTask<?> task = captureTask(action);
        completeTask(task);

        assertEquals(List.of(0, 1), attachedBeforeLookup);
        assertEquals(List.of(entry, secondEntry), attachedEntries);
    }

    private BackgroundTask<?> captureTask(DownloadFullTextAction action) {
        action.execute();

        ArgumentCaptor<BackgroundTask> taskCaptor = ArgumentCaptor.forClass(BackgroundTask.class);
        verify(taskExecutor).execute(taskCaptor.capture());
        return taskCaptor.getValue();
    }

    private void completeTask(BackgroundTask<?> task) throws Exception {
        task.call();
        runPendingUiActions();
    }

    private void runPendingUiActions() {
        List<Runnable> actions = List.copyOf(pendingUiActions);
        pendingUiActions.clear();
        actions.forEach(Runnable::run);
    }

    private class RecordingDownloadFullTextAction extends DownloadFullTextAction {
        private final List<BibEntry> downloadedEntries = new ArrayList<>();
        private final List<FetcherResult> downloadedResults = new ArrayList<>();

        RecordingDownloadFullTextAction(Function<BibEntry, Optional<FetcherResult>> fullTextFinder) {
            super(dialogService, stateManager, preferences, taskExecutor, fullTextFinder, pendingUiActions::add);
        }

        @Override
        void addLinkedFileFromURL(BibDatabaseContext databaseContext, FetcherResult result, BibEntry entry) {
            downloadedEntries.add(entry);
            downloadedResults.add(result);
        }
    }
}
