package org.jabref.toolkit.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import org.jabref.logic.importer.fetcher.CrossRef;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.quality.consistency.PrintedReferencesCheck;
import org.jabref.toolkit.converter.CygWinPathConverter;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Mixin;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;
import static picocli.CommandLine.ParentCommand;

// [impl->req~jabkit.cli.pdf-check-references~1]
@NullMarked
@Command(name = "check-references", description = "Report faults in the reference list printed in a PDF, such as DOIs given as URL, missing DOIs, and fields printed for some references only.")
class PdfCheckReferences implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfCheckReferences.class);

    @ParentCommand
    protected Pdf pdf;

    @Mixin
    private JabKit.SharedOptions sharedOptions;

    @Parameters(paramLabel = "FILE", converter = CygWinPathConverter.class, description = "PDF whose reference list is checked.")
    private Path inputFile;

    @Option(names = "--online", negatable = true, defaultValue = "true", fallbackValue = "true",
            description = "Look up missing DOIs and title capitalization at Crossref (default: ${DEFAULT-VALUE}).")
    private boolean online;

    @Override
    public Integer call() {
        if (!Files.exists(inputFile)) {
            System.err.println(Localization.lang("File %0 not found.", inputFile.toString()));
            return CommandLine.ExitCode.USAGE;
        }

        PrintedReferencesCheck check = new PrintedReferencesCheck(online ? new CrossRef(pdf.argumentProcessor.cliPreferences.getImporterPreferences()) : null);
        List<PrintedReferencesCheck.Finding> findings;
        try {
            findings = check.check(inputFile);
        } catch (IOException e) {
            LOGGER.error("Could not read {}", inputFile, e);
            return CommandLine.ExitCode.SOFTWARE;
        }

        findings.forEach(finding -> System.out.println(finding.reference() + " " + finding.message()));
        if (!sharedOptions.porcelain) {
            System.out.println(Localization.lang("%0 finding(s).", findings.size()));
        }
        // Findings give a non-zero exit, so CI can fail on them - same as "jabkit check"
        return findings.isEmpty() ? CommandLine.ExitCode.OK : 1;
    }
}
