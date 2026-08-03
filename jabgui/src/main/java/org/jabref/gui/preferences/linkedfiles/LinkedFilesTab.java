package org.jabref.gui.preferences.linkedfiles;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;

import org.jabref.gui.actions.StandardActions;
import org.jabref.gui.desktop.os.NativeDesktop;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.preferences.AbstractPreferenceTabView;
import org.jabref.gui.util.BindingsHelper;
import org.jabref.gui.util.ControlHelper;
import org.jabref.gui.util.ValueTableCellFactory;
import org.jabref.logic.help.HelpFile;
import org.jabref.logic.l10n.Localization;

public class LinkedFilesTab extends AbstractPreferenceTabView<LinkedFilesTabViewModel> {

    // Multiplier for row height based on font size
    private static final double FONT_HEIGHT_MULTIPLIER = 2.5;

    // Default row height if font is not available
    private static final double DEFAULT_ROW_HEIGHT = 30.0;

    // Estimate for header height (used in table prefHeight calculation)
    private static final double HEADER_HEIGHT_ESTIMATE = 1.1;

    // Minimum number of (empty) rows to reserve, so an empty table doesn't collapse to just the header
    private static final int MIN_ROW_COUNT = 1;

    /// Also the source of the mapping table's row height: it is a themed control in the tree, so its
    /// font tracks the configured font size.
    private final Label mappingNote = new Label(Localization.lang("When a linked file's absolute path cannot be found, try substituting a mapped directory for a matching prefix."));

    public LinkedFilesTab() {
        this.viewModel = new LinkedFilesTabViewModel(
                dialogService,
                preferences.getFilePreferences(),
                preferences.getAutoLinkPreferences());
        buildView();
    }

    @Override
    public String getTabName() {
        return Localization.lang("Linked files");
    }

    private void buildView() {
        mappingNote.setWrapText(true);

        setContent(form()

                .section(Localization.lang("File directory"), fileDirectory -> fileDirectory
                        .radioGroup(directory -> directory
                                .radio(Localization.lang("Main file directory"), viewModel.useMainFileDirectoryProperty(),
                                        mainDir -> mainDir.attachField(viewModel.mainFileDirectoryProperty(),
                                                path -> path.browse(viewModel::mainFileDirBrowse)
                                                            .disableWhen(viewModel.useBibLocationAsPrimaryProperty())
                                                            .validate(viewModel.mainFileDirValidationStatus())))
                                .radio(Localization.lang("Search and store files relative to library file location"),
                                        viewModel.useBibLocationAsPrimaryProperty(),
                                        relative -> relative.tooltip(Localization.lang("When downloading files, or moving linked files to the file directory, use the bib file location.")))))

                .section(Localization.lang("Open file explorer"), fileExplorer -> fileExplorer
                        .radioGroup(explorer -> explorer
                                .radio(Localization.lang("Open file explorer in files directory"), viewModel.openFileExplorerInFilesDirectoryProperty())
                                .radio(Localization.lang("Open file explorer in last opened directory"), viewModel.openFileExplorerInLastDirectoryProperty())))

                .section(Localization.lang("Autolink files"), autolinkFiles -> autolinkFiles
                        .radioGroup(autolink -> autolink
                                .radio(Localization.lang("Autolink files with names starting with the citation key"), viewModel.autolinkFileStartsBibtexProperty())
                                .radio(Localization.lang("Autolink only files that match the citation key"), viewModel.autolinkFileExactBibtexProperty())
                                .radio(Localization.lang("Use regular expression search"), viewModel.autolinkUseRegexProperty(),
                                        useRegex -> useRegex.attachField(viewModel.autolinkRegexKeyProperty(),
                                                regex -> regex.help(StandardActions.HELP_REGEX_SEARCH, HelpFile.REGEX_SEARCH)))))

                .section(Localization.lang("Fulltext Index"), fulltext -> fulltext
                        .checkbox(Localization.lang("Automatically index all linked files for fulltext search"), viewModel.fulltextIndexProperty()))

                .section(Localization.lang("Linked file name conventions"), conventions -> conventions
                        .checkbox(Localization.lang("Auto rename files if entry changes"), viewModel.autoRenameFilesOnChangeProperty())
                        .combo(Localization.lang("Filename format pattern"),
                                viewModel.defaultFileNamePatternsProperty(), viewModel.fileNamePatternProperty(), pattern -> pattern,
                                patternCombo -> patternCombo.configure(combo -> {
                                    combo.setEditable(true);
                                    combo.setPromptText(Localization.lang("Choose pattern"));
                                }))
                        .stringField(Localization.lang("File directory pattern"), viewModel.fileDirectoryPatternProperty()))

                .section(Localization.lang("Attached files"), attached -> attached
                        .checkbox(Localization.lang("Show confirmation dialog when deleting attached files"), viewModel.confirmLinkedFileDeleteProperty())
                        .checkbox(Localization.lang("Move deleted files to trash (instead of deleting them)"), viewModel.moveToTrashProperty(),
                                trash -> {
                                    if (!NativeDesktop.get().moveToTrashSupported()) {
                                        trash.disable();
                                    }
                                })
                        .checkbox(Localization.lang("Update linked file paths during entry transfer if the files are accessible"), viewModel.adjustLinkedFilesOnTransferProperty())
                        .checkbox(Localization.lang("Copy linked files on entry transfer when they would otherwise be inaccessible"), viewModel.copyLinkedFilesOnTransferProperty(),
                                copy -> copy.disableWhen(viewModel.adjustLinkedFilesOnTransferProperty().not()))
                        .checkbox(Localization.lang("Move linked files on entry transfer when they would otherwise be inaccessible"), viewModel.moveFilesOnTransferProperty(),
                                move -> move.disableWhen(viewModel.adjustLinkedFilesOnTransferProperty().not())))

                .section(Localization.lang("Directory mapping"), mapping -> mapping
                        .custom(mappingNote)
                        .custom(buildDirectoryMappingTable())
                        .buttonRow(ControlHelper.labelledIconButton(IconTheme.JabRefIcons.ADD_NOBOX, Localization.lang("Add mapping"), viewModel::addDirectoryMapping)))

                .build());
    }

    private TableView<DirectoryMappingItem> buildDirectoryMappingTable() {
        TableView<DirectoryMappingItem> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(viewModel.getDirectoryMappings());

        TableColumn<DirectoryMappingItem, String> directory = new TableColumn<>(Localization.lang("Stored directory"));
        directory.setEditable(true);
        directory.setCellValueFactory(data -> data.getValue().directoryProperty());
        directory.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<DirectoryMappingItem, String> mappedDirectory = new TableColumn<>(Localization.lang("Local directory"));
        mappedDirectory.setEditable(true);
        mappedDirectory.setCellValueFactory(data -> data.getValue().mappedDirectoryProperty());
        mappedDirectory.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<DirectoryMappingItem, Boolean> delete = new TableColumn<>();
        delete.setMinWidth(40.0);
        delete.setMaxWidth(40.0);
        delete.setCellValueFactory(_ -> BindingsHelper.constantOf(true));
        new ValueTableCellFactory<DirectoryMappingItem, Boolean>()
                .withGraphic(_ -> IconTheme.JabRefIcons.DELETE_ENTRY.getGraphicNode())
                .withOnMouseClickedEvent((item, _) -> _ -> viewModel.removeDirectoryMapping(item))
                .install(delete);

        table.getColumns().add(directory);
        table.getColumns().add(mappedDirectory);
        table.getColumns().add(delete);

        // Size the table to its content so it doesn't reserve empty striped rows inside the form.
        DoubleBinding rowHeight = Bindings.createDoubleBinding(
                () -> mappingNote.getFont() != null ? mappingNote.getFont().getSize() * FONT_HEIGHT_MULTIPLIER : DEFAULT_ROW_HEIGHT,
                mappingNote.fontProperty());
        table.fixedCellSizeProperty().bind(rowHeight);
        table.prefHeightProperty().bind(
                Bindings.max(Bindings.size(table.getItems()), MIN_ROW_COUNT)
                        .add(HEADER_HEIGHT_ESTIMATE)
                        .multiply(rowHeight));
        return table;
    }
}
