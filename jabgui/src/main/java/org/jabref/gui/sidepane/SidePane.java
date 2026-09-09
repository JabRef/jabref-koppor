package org.jabref.gui.sidepane;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import org.jabref.gui.DialogService;
import org.jabref.gui.LibraryTabContainer;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.clipboard.ClipBoardManager;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.logic.ai.AiService;
import org.jabref.logic.git.util.GitHandlerRegistry;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.util.FileUpdateMonitor;

/// The left dock: one tab per visible side pane (groups, web search, OpenOffice), like the docks of the Godot
/// editor. The tabs mirror [StateManager#getVisibleSidePaneComponents()]; dragging a tab reorders that list.
public class SidePane extends TabPane {
    private final SidePaneViewModel viewModel;
    private final GuiPreferences preferences;
    private final StateManager stateManager;

    // These bindings need to be stored, otherwise they are garbage collected
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Map<SidePaneType, BooleanBinding> visibleBindings = new HashMap<>();

    public SidePane(LibraryTabContainer tabContainer,
                    GuiPreferences preferences,
                    JournalAbbreviationRepository abbreviationRepository,
                    TaskExecutor taskExecutor,
                    DialogService dialogService,
                    AiService aiService,
                    StateManager stateManager,
                    FileUpdateMonitor fileUpdateMonitor,
                    BibEntryTypesManager entryTypesManager,
                    ClipBoardManager clipBoardManager,
                    GitHandlerRegistry gitHandlerRegistry) {
        this.stateManager = stateManager;
        this.preferences = preferences;
        this.viewModel = new SidePaneViewModel(
                tabContainer,
                preferences,
                abbreviationRepository,
                stateManager,
                taskExecutor,
                dialogService,
                aiService,
                fileUpdateMonitor,
                entryTypesManager,
                clipBoardManager,
                gitHandlerRegistry);

        getStyleClass().add("dock");
        setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        setTabDragPolicy(TabDragPolicy.REORDER);

        stateManager.getVisibleSidePaneComponents().addListener((ListChangeListener<SidePaneType>) _ -> updateView());
        getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasPermutated()) {
                    // The user dragged a tab; TabPane reorders its list in place, so the state has to follow
                    viewModel.reorder(getTabs().stream().map(tab -> ((SidePaneComponent) tab).getSidePaneType()).toList());
                }
            }
        });
        updateView();
    }

    private void updateView() {
        List<Tab> tabs = stateManager.getVisibleSidePaneComponents().stream()
                                     .<Tab>map(viewModel::getSidePaneComponent)
                                     .toList();
        if (getTabs().equals(tabs)) {
            // Already in sync, e.g. after a drag reorder that the state just adopted
            return;
        }
        List<Tab> added = new ArrayList<>(tabs);
        added.removeAll(getTabs());
        getTabs().setAll(tabs);
        if (!added.isEmpty()) {
            // A pane the user just switched on should be the one on screen
            getSelectionModel().select(added.getLast());
        }
    }

    public BooleanBinding paneVisibleBinding(SidePaneType pane) {
        BooleanBinding visibility = Bindings.createBooleanBinding(
                () -> stateManager.getVisibleSidePaneComponents().contains(pane),
                stateManager.getVisibleSidePaneComponents());
        visibleBindings.put(pane, visibility);
        return visibility;
    }

    public SimpleCommand getToggleCommandFor(SidePaneType sidePane) {
        return new TogglePaneAction(stateManager, sidePane, preferences.getSidePanePreferences());
    }

    public SidePaneComponent getSidePaneComponent(SidePaneType type) {
        return viewModel.getSidePaneComponent(type);
    }
}
