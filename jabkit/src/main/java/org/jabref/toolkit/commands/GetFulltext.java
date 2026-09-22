package org.jabref.toolkit.commands;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.jabref.logic.externalfiles.FulltextDownloader;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.util.StandardFileType;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;
import org.jabref.toolkit.exception.ExportServiceException;
import org.jabref.toolkit.exception.ImportServiceException;
import org.jabref.toolkit.service.ExportService;
import org.jabref.toolkit.service.ImportService;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

// [impl->req~jabkit.cli.get-fulltext-report~1]
@Command(name = "get-fulltext", description = "Download the full text PDFs of the entries of a library and link them.")
class GetFulltext implements Callable<Integer> {

    protected FulltextDownloader fulltextDownloader;

    @ParentCommand
    private JabKit argumentProcessor;

    @Mixin
    private JabKit.SharedOptions sharedOptions;

    @Mixin
    private InputOption inputOption = new InputOption();

    @Option(names = "--output", description = "Output .bib file (default: the input file is updated)")
    private Path outputFile;

    void initFields() {
        CliPreferences preferences = argumentProcessor.cliPreferences;
        fulltextDownloader = new FulltextDownloader(preferences.getImportFormatPreferences(), preferences.getImporterPreferences(), preferences.getFilePreferences());
    }

    @Override
    public Integer call() throws ImportServiceException, ExportServiceException {
        initFields();
        CliPreferences preferences = argumentProcessor.cliPreferences;
        Path inputFile = inputOption.getInputFile(preferences);
        ParserResult parserResult = ImportService.importBibTexFile(inputFile, preferences, sharedOptions.porcelain);
        BibDatabaseContext databaseContext = parserResult.getDatabaseContext();
        // Relative file directories and the "store files next to the library" preference resolve against it
        databaseContext.setDatabasePath(inputFile.toAbsolutePath());

        int downloaded = 0;
        int notFound = 0;
        int skipped = 0;
        int failed = 0;
        for (BibEntry entry : databaseContext.getEntries()) {
            String citationKey = entry.getCitationKey().orElse(Localization.lang("undefined"));
            // Keeps a re-run from sending requests to publishers for entries it already handled
            if (hasLocalPdf(entry)) {
                System.out.println(Localization.lang("Full text document for entry %0 already linked.", citationKey));
                skipped++;
                continue;
            }
            switch (fulltextDownloader.download(databaseContext, entry)) {
                case FulltextDownloader.Result.Downloaded downloadedFile -> {
                    System.out.println(Localization.lang("Downloaded full text document for entry %0: %1", citationKey, downloadedFile.file()));
                    downloaded++;
                }
                case FulltextDownloader.Result.NotFound _ -> {
                    System.out.println(Localization.lang("No full text document found for entry %0.", citationKey));
                    notFound++;
                }
                case FulltextDownloader.Result.AlreadyLinked _ -> {
                    System.out.println(Localization.lang("Full text document for entry %0 already linked.", citationKey));
                    skipped++;
                }
                case FulltextDownloader.Result.Duplicate _ -> {
                    System.out.println(Localization.lang("Full text document for entry %0 is a duplicate of an existing file.", citationKey));
                    skipped++;
                }
                case FulltextDownloader.Result.Failed failure -> {
                    System.out.println(Localization.lang("Could not download the full text document for entry %0: %1", citationKey, failure.message()));
                    failed++;
                }
                case FulltextDownloader.Result.NoFileDirectory _ -> {
                    System.err.println(Localization.lang("No existing file directory to download the full text documents to."));
                    return CommandLine.ExitCode.SOFTWARE;
                }
            }
        }

        if (!sharedOptions.porcelain) {
            System.out.println(Localization.lang("Full text documents: %0 downloaded, %1 not found, %2 skipped, %3 failed.",
                    downloaded, notFound, skipped, failed));
        }

        if (downloaded > 0 || outputFile != null) {
            ExportService.create(preferences, sharedOptions.porcelain)
                         .saveDatabaseContext(databaseContext, outputFile != null ? outputFile : inputFile);
        }
        return failed > 0 ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
    }

    private static boolean hasLocalPdf(BibEntry entry) {
        return entry.getFiles().stream()
                    .anyMatch(file -> !LinkedFile.isOnlineLink(file.getLink())
                            && StandardFileType.PDF.getName().equalsIgnoreCase(file.getFileType()));
    }
}
