package org.jabref.gui.sidepane;

import java.util.EnumSet;

import javafx.collections.SetChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import org.jabref.gui.DialogService;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.groups.GroupModeViewModel;
import org.jabref.gui.groups.GroupViewMode;
import org.jabref.gui.groups.GroupsPreferences;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.util.ControlHelper;
import org.jabref.logic.l10n.Localization;

public class GroupsSidePaneComponent extends SidePaneComponent {
    private final GroupsPreferences groupsPreferences;
    private final DialogService dialogService;
    private final Button intersectionUnionToggle = ControlHelper.iconButton(IconTheme.JabRefIcons.GROUP_INTERSECTION);
    private final ToggleButton filterToggle = ControlHelper.iconToggleButton(IconTheme.JabRefIcons.FILTER);
    private final ToggleButton invertToggle = ControlHelper.iconToggleButton(IconTheme.JabRefIcons.INVERT);

    public GroupsSidePaneComponent(SimpleCommand closeCommand,
                                   SidePaneContentFactory contentFactory,
                                   GroupsPreferences groupsPreferences,
                                   DialogService dialogService) {
        super(SidePaneType.GROUPS, closeCommand, contentFactory);
        // The walkthrough highlights the groups pane by this id
        getContainer().setId("groups-side-pane");
        this.groupsPreferences = groupsPreferences;
        this.dialogService = dialogService;

        setupInvertToggle();
        setupFilterToggle();
        setupIntersectionUnionToggle();
        HBox toolbar = new HBox(invertToggle, filterToggle, intersectionUnionToggle);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.getStyleClass().add("sidePaneComponentHeader");
        setToolbar(toolbar);

        groupsPreferences.groupViewModeProperty().addListener((SetChangeListener<GroupViewMode>) change -> {
            GroupModeViewModel modeViewModel = new GroupModeViewModel(groupsPreferences.groupViewModeProperty());
            intersectionUnionToggle.setGraphic(modeViewModel.getUnionIntersectionGraphic());
            intersectionUnionToggle.setTooltip(modeViewModel.getUnionIntersectionTooltip());
        });
    }

    private void setupIntersectionUnionToggle() {
        intersectionUnionToggle.setOnAction(event -> new ToggleUnionIntersectionAction().execute());
    }

    private void setupFilterToggle() {
        filterToggle.setTooltip(new Tooltip(Localization.lang("Filter by groups")));
        filterToggle.setSelected(groupsPreferences.groupViewModeProperty().contains(GroupViewMode.FILTER));
        filterToggle.selectedProperty().addListener((observable, oldValue, newValue) -> groupsPreferences.setGroupViewMode(GroupViewMode.FILTER, newValue));
    }

    private void setupInvertToggle() {
        invertToggle.setTooltip(new Tooltip(Localization.lang("Invert groups")));
        invertToggle.setSelected(groupsPreferences.groupViewModeProperty().contains(GroupViewMode.INVERT));
        invertToggle.selectedProperty().addListener((observable, oldValue, newValue) -> groupsPreferences.setGroupViewMode(GroupViewMode.INVERT, newValue));
    }

    private class ToggleUnionIntersectionAction extends SimpleCommand {

        @Override
        public void execute() {
            EnumSet<GroupViewMode> mode = groupsPreferences.getGroupViewMode();

            if (!mode.contains(GroupViewMode.INTERSECTION)) {
                groupsPreferences.setGroupViewMode(GroupViewMode.INTERSECTION, true);
                dialogService.notify(Localization.lang("Group view mode set to intersection"));
            } else {
                groupsPreferences.setGroupViewMode(GroupViewMode.INTERSECTION, false);
                dialogService.notify(Localization.lang("Group view mode set to union"));
            }
        }
    }
}
