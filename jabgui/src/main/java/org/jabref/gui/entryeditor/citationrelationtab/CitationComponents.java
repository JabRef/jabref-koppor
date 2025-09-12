package org.jabref.gui.entryeditor.citationrelationtab;

import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import org.controlsfx.control.CheckListView;
import org.jabref.logic.importer.fetcher.citation.CitationFetcher;
import org.jabref.model.entry.BibEntry;

public record CitationComponents(
    BibEntry entry,
    CheckListView<CitationRelationItem> listView,
    Button abortButton,
    Button refreshButton,
    CitationFetcher.SearchType searchType,
    Button importButton,
    ProgressIndicator progress
) {}
