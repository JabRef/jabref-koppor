package org.jabref.gui.mergeentries;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.undo.UndoManager;

import org.jabref.gui.DialogService;
import org.jabref.gui.mergeentries.threewaymerge.EntriesMergeResult;
import org.jabref.gui.mergeentries.threewaymerge.MergeEntriesDialog;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.undo.NamedCompound;
import org.jabref.gui.undo.UndoableChangeType;
import org.jabref.gui.undo.UndoableFieldChange;
import org.jabref.logic.importer.EntryBasedFetcher;
import org.jabref.logic.importer.FetcherClientException;
import org.jabref.logic.importer.FetcherServerException;
import org.jabref.logic.importer.IdBasedFetcher;
import org.jabref.logic.importer.ImportCleanup;
import org.jabref.logic.importer.WebFetcher;
import org.jabref.logic.importer.WebFetchers;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.BackgroundTask;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldFactory;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.EntryType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for fetching and merging bibliographic information
 */
public class FetchAndMergeEntry {

    // All identifiers listed here should also appear at {@link org.jabref.logic.importer.CompositeIdFetcher#performSearchById}
    public static List<Field> SUPPORTED_FIELDS = Arrays.asList(StandardField.DOI, StandardField.EPRINT, StandardField.ISBN);

    private static final Logger LOGGER = LoggerFactory.getLogger(FetchAndMergeEntry.class);
    private final DialogService dialogService;
    private final UndoManager undoManager;
    private final BibDatabaseContext bibDatabaseContext;
    private final TaskExecutor taskExecutor;
    private final GuiPreferences preferences;

    public FetchAndMergeEntry(BibDatabaseContext bibDatabaseContext,
                              TaskExecutor taskExecutor,
                              GuiPreferences preferences,
                              DialogService dialogService,
                              UndoManager undoManager) {
        this.bibDatabaseContext = bibDatabaseContext;
        this.taskExecutor = taskExecutor;
        this.preferences = preferences;
        this.dialogService = dialogService;
        this.undoManager = undoManager;
    }

    public void fetchAndMerge(BibEntry entry) {
        fetchAndMerge(entry, SUPPORTED_FIELDS);
    }

    public void fetchAndMerge(BibEntry entry, Field field) {
        fetchAndMerge(entry, List.of(field));
    }

    public void fetchAndMerge(BibEntry entry, List<Field> fields) {
        for (Field field : fields) {
            Optional<String> fieldContent = entry.getField(field);
            if (fieldContent.isPresent()) {
                Optional<IdBasedFetcher> fetcher = WebFetchers.getIdBasedFetcherForField(field, preferences.getImportFormatPreferences());
                fetcher.ifPresent(idBasedFetcher -> BackgroundTask.wrap(() -> idBasedFetcher.performSearchById(fieldContent.get()))
                                                                  .onSuccess(fetchedEntry -> {
                                                                      ImportCleanup cleanup = ImportCleanup.targeting(bibDatabaseContext.getMode(), preferences.getFieldPreferences());
                                                                      String type = field.getDisplayName();
                                                                      if (fetchedEntry.isPresent()) {
                                                                          cleanup.doPostCleanup(fetchedEntry.get());
                                                                          showMergeDialog(entry, fetchedEntry.get(), idBasedFetcher);
                                                                      } else {
                                                                          dialogService.notify(Localization.lang("Cannot get info based on given %0: %1", type, fieldContent.get()));
                                                                      }
                                                                  })
                                                                  .onFailure(exception -> {
                                                                      LOGGER.error("Error while fetching bibliographic information", exception);
                                                                      if (exception instanceof FetcherClientException) {
                                                                          dialogService.showInformationDialogAndWait(Localization.lang("Fetching information using %0", idBasedFetcher.getName()), Localization.lang("No data was found for the identifier"));
                                                                      } else if (exception instanceof FetcherServerException) {
                                                                          dialogService.showInformationDialogAndWait(Localization.lang("Fetching information using %0", idBasedFetcher.getName()), Localization.lang("Server not available"));
                                                                      } else {
                                                                          dialogService.showInformationDialogAndWait(Localization.lang("Fetching information using %0", idBasedFetcher.getName()), Localization.lang("Error occurred %0", exception.getMessage()));
                                                                      }
                                                                  })
                                                                  .executeWith(taskExecutor));
            } else {
                dialogService.notify(Localization.lang("No %0 found", field.getDisplayName()));
            }
        }
    }

    private void showMergeDialog(BibEntry originalEntry, BibEntry fetchedEntry, WebFetcher fetcher) {
        MergeEntriesDialog dialog = new MergeEntriesDialog(originalEntry, fetchedEntry, preferences);
        dialog.setTitle(Localization.lang("Merge entry with %0 information", fetcher.getName()));
        dialog.setLeftHeaderText(Localization.lang("Original entry"));
        dialog.setRightHeaderText(Localization.lang("Entry from %0", fetcher.getName()));
        dialog.autoSelectBetterFields();
        Optional<BibEntry> mergedEntry = dialogService.showCustomDialogAndWait(dialog).map(EntriesMergeResult::mergedEntry);

        if (mergedEntry.isPresent()) {
            NamedCompound ce = new NamedCompound(Localization.lang("Merge entry with %0 information", fetcher.getName()));

            // Updated the original entry with the new fields
            Set<Field> jointFields = new TreeSet<>(Comparator.comparing(Field::getName));
            jointFields.addAll(mergedEntry.get().getFields());
            Set<Field> originalFields = new TreeSet<>(Comparator.comparing(Field::getName));
            originalFields.addAll(originalEntry.getFields());
            boolean edited = false;

            // entry type
            EntryType oldType = originalEntry.getType();
            EntryType newType = mergedEntry.get().getType();

            if (!oldType.equals(newType)) {
                originalEntry.setType(newType);
                ce.addEdit(new UndoableChangeType(originalEntry, oldType, newType));
                edited = true;
            }

            // fields
            for (Field field : jointFields) {
                Optional<String> originalString = originalEntry.getField(field);
                Optional<String> mergedString = mergedEntry.get().getField(field);
                if (originalString.isEmpty() || !originalString.equals(mergedString)) {
                    originalEntry.setField(field, mergedString.get()); // mergedString always present
                    ce.addEdit(new UndoableFieldChange(originalEntry, field, originalString.orElse(null),
                            mergedString.get()));
                    edited = true;
                }
            }

            // Remove fields which are not in the merged entry, unless they are internal fields
            for (Field field : originalFields) {
                if (!jointFields.contains(field) && !FieldFactory.isInternalField(field)) {
                    Optional<String> originalString = originalEntry.getField(field);
                    originalEntry.clearField(field);
                    ce.addEdit(new UndoableFieldChange(originalEntry, field, originalString.get(), null)); // originalString always present
                    edited = true;
                }
            }

            if (edited) {
                ce.end();
                undoManager.addEdit(ce);
                dialogService.notify(Localization.lang("Updated entry with info from %0", fetcher.getName()));
            } else {
                dialogService.notify(Localization.lang("No information added"));
            }
        } else {
            dialogService.notify(Localization.lang("Canceled merging entries"));
        }
    }

    public void fetchAndMerge(BibEntry entry, EntryBasedFetcher fetcher) {
        BackgroundTask.wrap(() -> fetcher.performSearch(entry).stream().findFirst())
                      .onSuccess(fetchedEntry -> {
                          if (fetchedEntry.isPresent()) {
                              ImportCleanup cleanup = ImportCleanup.targeting(bibDatabaseContext.getMode(), preferences.getFieldPreferences());
                              cleanup.doPostCleanup(fetchedEntry.get());
                              showMergeDialog(entry, fetchedEntry.get(), fetcher);
                          } else {
                              dialogService.notify(Localization.lang("Could not find any bibliographic information."));
                          }
                      })
                      .onFailure(exception -> {
                          LOGGER.error("Error while fetching entry with {} ", fetcher.getName(), exception);
                          dialogService.showErrorDialogAndWait(Localization.lang("Error while fetching from %0", fetcher.getName()), exception);
                      })
                      .executeWith(taskExecutor);
    }
}
