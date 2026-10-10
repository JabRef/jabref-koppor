package org.jabref.logic.externalfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

import org.jabref.logic.FilePreferences;
import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.FetcherResult;
import org.jabref.logic.importer.FulltextFetchers;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.ImporterPreferences;
import org.jabref.logic.net.URLDownload;
import org.jabref.logic.util.StandardFileType;
import org.jabref.logic.util.io.FileNameUniqueness;
import org.jabref.logic.util.io.FileUtil;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Searches the full text PDF of an entry online, downloads it into the library's file directory and links it.
///
/// UI-independent, so that `jabkit get-fulltexts` can use it; the GUI's "Search full text documents online" still has
/// its own implementation.
@NullMarked
public class FulltextDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(FulltextDownloader.class);

    public sealed interface Result {
        /// @param file the downloaded file, absolute
        record Downloaded(Path file) implements Result {
        }

        record NotFound() implements Result {
        }

        record AlreadyLinked() implements Result {
        }

        /// The downloaded file had the same content as an existing file in the target directory and was deleted again.
        record Duplicate() implements Result {
        }

        record NoFileDirectory() implements Result {
        }

        record Failed(String message) implements Result {
        }
    }

    private final FilePreferences filePreferences;
    private final Function<BibEntry, Optional<FetcherResult>> fullTextFinder;

    public FulltextDownloader(ImportFormatPreferences importFormatPreferences,
                              ImporterPreferences importerPreferences,
                              FilePreferences filePreferences) {
        this(filePreferences, new FulltextFetchers(importFormatPreferences, importerPreferences)::findFullTextPDF);
    }

    FulltextDownloader(FilePreferences filePreferences, Function<BibEntry, Optional<FetcherResult>> fullTextFinder) {
        this.filePreferences = filePreferences;
        this.fullTextFinder = fullTextFinder;
    }

    /// Links the downloaded file to the entry; the caller has to save the library.
    public Result download(BibDatabaseContext databaseContext, BibEntry entry) {
        return databaseContext.getFirstExistingFileDir(filePreferences)
                              .map(targetDirectory -> fullTextFinder.apply(entry)
                                                                    .map(found -> download(databaseContext, entry, found, targetDirectory))
                                                                    .orElseGet(Result.NotFound::new))
                              .orElseGet(Result.NoFileDirectory::new);
    }

    private Result download(BibDatabaseContext databaseContext, BibEntry entry, FetcherResult found, Path targetDirectory) {
        String url = found.source().toExternalForm();
        if (entry.getFiles().stream().anyMatch(file -> url.equals(file.getLink()) || url.equals(file.getSourceUrl()))) {
            return new Result.AlreadyLinked();
        }

        String fileName = new LinkedFileHandler(new LinkedFile(found.source(), ""), entry, databaseContext, filePreferences)
                .getSuggestedFileName("pdf");
        Path directory = targetDirectory.resolve(FileUtil.createDirNameFromPattern(databaseContext.getDatabase(), entry, filePreferences.getFileDirectoryPattern()));
        Path destination = directory.resolve(FileNameUniqueness.getNonOverWritingFileName(directory, fileName));
        try {
            Files.createDirectories(directory);
            URLDownload download = new URLDownload(found.source());
            found.headers().forEach(download::addHeader);
            download.toFile(destination);
            if (FileNameUniqueness.isDuplicatedFile(directory, destination.getFileName(), LOGGER::info)) {
                return new Result.Duplicate();
            }
        } catch (IOException | FetcherException e) {
            LOGGER.warn("Could not download {}", FetcherException.getRedactedUrl(url), e);
            deletePartialDownload(destination);
            return new Result.Failed(e.getLocalizedMessage());
        }

        LinkedFile linkedFile = new LinkedFile("", FileUtil.relativize(destination, databaseContext.getFileDirectories(filePreferences)), StandardFileType.PDF.getName());
        if (filePreferences.shouldKeepDownloadUrl()) {
            linkedFile.setSourceURL(url);
        }
        entry.addFile(linkedFile);
        return new Result.Downloaded(destination);
    }

    private static void deletePartialDownload(Path destination) {
        try {
            Files.deleteIfExists(destination);
        } catch (IOException e) {
            LOGGER.warn("Could not delete partially downloaded {}", destination, e);
        }
    }
}
