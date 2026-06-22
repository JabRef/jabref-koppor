package org.jabref.toolkit.commands;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.jabref.logic.cleanup.CleanupPreferences;
import org.jabref.logic.cleanup.CleanupPreferences.CleanupStep;
import org.jabref.logic.cleanup.CleanupWorker;
import org.jabref.logic.cleanup.FieldFormatterCleanupActions;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.FieldChange;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.toolkit.converter.CygWinPathConverter;

import com.airhacks.afterburner.injection.Injector;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Mixin;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.ParentCommand;

/// Applies a curated set of cleanup operations to every entry of a library ("auto-fix").
///
/// The set is intentionally restricted to operations that only touch the bibliographic data and do
/// not need filesystem access (no PDF renaming, file linking or XMP). It currently runs:
/// {@link CleanupStep#CLEAN_UP_DOI}, {@link CleanupStep#CLEANUP_EPRINT}, {@link CleanupStep#CLEAN_UP_URL}
/// plus the default field-formatter save actions (e.g. ISSN, pages, date and month normalization).
@Command(name = "autofix", description = "Apply a set of cleanup operations to all entries of a library.")
class AutoFix implements Callable<Integer> {

    /// Cleanup steps that operate on bibliographic data only (no filesystem access required).
    private static final Set<CleanupStep> AUTOFIX_STEPS = EnumSet.of(
            CleanupStep.CLEAN_UP_DOI,
            CleanupStep.CLEAN_UP_ARXIV_DOI,
            CleanupStep.CLEANUP_EPRINT,
            CleanupStep.CLEAN_UP_URL);

    @ParentCommand
    private JabKit jabKit;

    @Mixin
    private JabKit.SharedOptions sharedOptions = new JabKit.SharedOptions();

    @Mixin
    private InputOption inputOption = new InputOption();

    @Option(names = {"--output"}, converter = CygWinPathConverter.class,
            description = "Output file. If omitted, the fixed library is written to standard output.")
    private Path outputFile;

    @Override
    public Integer call() {
        Path inputFile = inputOption.getInputFile();

        JabKit.ImportOutcome outcome = JabKit.importBibtexLibrary(inputFile, jabKit.cliPreferences, sharedOptions.porcelain);
        if (outcome.parserResult() == null) {
            return outcome.exitCode();
        }

        BibDatabaseContext databaseContext = outcome.parserResult().getDatabaseContext();

        CleanupPreferences cleanupPreferences = new CleanupPreferences(
                AUTOFIX_STEPS,
                new FieldFormatterCleanupActions(true, FieldFormatterCleanupActions.DEFAULT_SAVE_ACTIONS));

        CleanupWorker worker = new CleanupWorker(
                databaseContext,
                jabKit.cliPreferences.getFilePreferences(),
                jabKit.cliPreferences.getTimestampPreferences(),
                jabKit.cliPreferences.getJournalAbbreviationPreferences().shouldUseFJournalField(),
                Injector.instantiateModelOrService(JournalAbbreviationRepository.class));

        int modifiedEntries = 0;
        for (BibEntry entry : databaseContext.getDatabase().getEntries()) {
            List<FieldChange> changes = worker.cleanup(cleanupPreferences, entry);
            if (!changes.isEmpty()) {
                modifiedEntries++;
            }
        }

        if (!sharedOptions.porcelain) {
            System.out.println(Localization.lang("%0 entry(s) needed a clean up", Integer.toString(modifiedEntries)));
        }

        if (outputFile == null) {
            return JabKit.outputDatabaseContext(jabKit.cliPreferences, databaseContext);
        }

        JabKit.saveDatabaseContext(
                jabKit.cliPreferences,
                jabKit.entryTypesManager,
                databaseContext,
                outputFile);
        return 0;
    }
}
