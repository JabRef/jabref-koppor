package org.jabref.gui.sidepane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.swing.undo.UndoManager;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import org.jabref.gui.AbstractViewModel;
import org.jabref.gui.ClipBoardManager;
import org.jabref.gui.DialogService;
import org.jabref.gui.LibraryTabContainer;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.entryeditor.AdaptVisibleTabs;
import org.jabref.gui.frame.SidePanePreferences;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.logic.ai.AiService;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.util.FileUpdateMonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SidePaneViewModel extends AbstractViewModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(SidePaneViewModel.class);

    private final Map<SidePaneType, SidePaneComponent> sidePaneComponentLookup = new HashMap<>();

    private final GuiPreferences preferences;
    private final StateManager stateManager;
    private final SidePaneContentFactory sidePaneContentFactory;
    private final AdaptVisibleTabs adaptVisibleTabs;
    private final DialogService dialogService;

    public SidePaneViewModel(LibraryTabContainer tabContainer,
                             GuiPreferences preferences,
                             JournalAbbreviationRepository abbreviationRepository,
                             StateManager stateManager,
                             TaskExecutor taskExecutor,
                             AdaptVisibleTabs adaptVisibleTabs,
                             DialogService dialogService,
                             AiService aiService,
                             FileUpdateMonitor fileUpdateMonitor,
                             BibEntryTypesManager entryTypesManager,
                             ClipBoardManager clipBoardManager,
                             UndoManager undoManager) {
        this.preferences = preferences;
        this.stateManager = stateManager;
        this.adaptVisibleTabs = adaptVisibleTabs;
        this.dialogService = dialogService;
        this.sidePaneContentFactory = new SidePaneContentFactory(
                tabContainer,
                preferences,
                abbreviationRepository,
                taskExecutor,
                dialogService,
                aiService,
                stateManager,
                adaptVisibleTabs,
                fileUpdateMonitor,
                entryTypesManager,
                clipBoardManager,
                undoManager);

        preferences.getSidePanePreferences().visiblePanes().forEach(this::show);
        getPanes().addListener((ListChangeListener<? super SidePaneType>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    preferences.getSidePanePreferences().visiblePanes().add(change.getAddedSubList().getFirst());
                } else if (change.wasRemoved()) {
                    preferences.getSidePanePreferences().visiblePanes().remove(change.getRemoved().getFirst());
                }
            }
        });
    }

    protected SidePaneComponent getSidePaneComponent(SidePaneType pane) {
        SidePaneComponent sidePaneComponent = sidePaneComponentLookup.get(pane);
        if (sidePaneComponent == null) {
            sidePaneComponent = switch (pane) {
                case GROUPS -> new GroupsSidePaneComponent(
                        new ClosePaneAction(pane),
                        new MoveUpAction(pane),
                        new MoveDownAction(pane),
                        sidePaneContentFactory,
                        preferences.getGroupsPreferences(),
                        dialogService);
                case WEB_SEARCH, OPEN_OFFICE -> new SidePaneComponent(pane,
                        new ClosePaneAction(pane),
                        new MoveUpAction(pane),
                        new MoveDownAction(pane),
                        sidePaneContentFactory);
            };
            sidePaneComponentLookup.put(pane, sidePaneComponent);
        }
        return sidePaneComponent;
    }

    /**
     * Stores the current configuration of visible panes in the preferences, so that we show panes at the preferred
     * position next time.
     */
    private void updatePreferredPositions() {
        Map<SidePaneType, Integer> preferredPositions = new HashMap<>(preferences.getSidePanePreferences()
                                                                                 .getPreferredPositions());
        IntStream.range(0, getPanes().size()).forEach(i -> preferredPositions.put(getPanes().get(i), i));
        preferences.getSidePanePreferences().setPreferredPositions(preferredPositions);
    }

    public void moveUp(SidePaneType pane) {
        if (getPanes().contains(pane)) {
            int currentPosition = getPanes().indexOf(pane);
            if (currentPosition > 0) {
                int newPosition = currentPosition - 1;
                swap(getPanes(), currentPosition, newPosition);
                updatePreferredPositions();
            } else {
                LOGGER.debug("SidePaneComponent is already at the bottom");
            }
        } else {
            LOGGER.warn("SidePaneComponent {} not visible", pane.getTitle());
        }
    }

    public void moveDown(SidePaneType pane) {
        if (getPanes().contains(pane)) {
            int currentPosition = getPanes().indexOf(pane);
            if (currentPosition < (getPanes().size() - 1)) {
                int newPosition = currentPosition + 1;
                swap(getPanes(), currentPosition, newPosition);
                updatePreferredPositions();
            } else {
                LOGGER.debug("SidePaneComponent {} is already at the top", pane.getTitle());
            }
        } else {
            LOGGER.warn("SidePaneComponent {} not visible", pane.getTitle());
        }
    }

    private void show(SidePaneType pane) {
        if (!getPanes().contains(pane)) {
            getPanes().add(pane);
            getPanes().sort(new PreferredIndexSort(preferences.getSidePanePreferences()));
        } else {
            LOGGER.warn("SidePaneComponent {} not visible", pane.getTitle());
        }
    }

    private ObservableList<SidePaneType> getPanes() {
        return stateManager.getVisibleSidePaneComponents();
    }

    private <T> void swap(ObservableList<T> observableList, int i, int j) {
        List<T> placeholder = new ArrayList<>(observableList);
        Collections.swap(placeholder, i, j);
        observableList.sort(Comparator.comparingInt(placeholder::indexOf));
    }

    /**
     * Helper class for sorting visible side panes based on their preferred position.
     */
    protected static class PreferredIndexSort implements Comparator<SidePaneType> {

        private final Map<SidePaneType, Integer> preferredPositions;

        public PreferredIndexSort(SidePanePreferences sidePanePreferences) {
            this.preferredPositions = sidePanePreferences.getPreferredPositions();
        }

        @Override
        public int compare(SidePaneType type1, SidePaneType type2) {
            int pos1 = preferredPositions.getOrDefault(type1, 0);
            int pos2 = preferredPositions.getOrDefault(type2, 0);
            return Integer.compare(pos1, pos2);
        }
    }

    private class MoveUpAction extends SimpleCommand {
        private final SidePaneType toMoveUpPane;

        public MoveUpAction(SidePaneType toMoveUpPane) {
            this.toMoveUpPane = toMoveUpPane;
        }

        @Override
        public void execute() {
            moveUp(toMoveUpPane);
        }
    }

    private class MoveDownAction extends SimpleCommand {
        private final SidePaneType toMoveDownPane;

        public MoveDownAction(SidePaneType toMoveDownPane) {
            this.toMoveDownPane = toMoveDownPane;
        }

        @Override
        public void execute() {
            moveDown(toMoveDownPane);
        }
    }

    public class ClosePaneAction extends SimpleCommand {
        private final SidePaneType toClosePane;

        public ClosePaneAction(SidePaneType toClosePane) {
            this.toClosePane = toClosePane;
        }

        @Override
        public void execute() {
            stateManager.getVisibleSidePaneComponents().remove(toClosePane);
        }
    }
}
