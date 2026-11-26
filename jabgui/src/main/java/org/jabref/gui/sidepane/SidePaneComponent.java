package org.jabref.gui.sidepane;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;

import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.groups.GroupTreeView;

/// One dock tab of the [SidePane]: icon + title in the tab strip, the pane's content below.
public class SidePaneComponent extends Tab {
    private final SidePaneType sidePaneType;
    private final BorderPane container = new BorderPane();

    public SidePaneComponent(SidePaneType sidePaneType,
                             SimpleCommand closeCommand,
                             SidePaneContentFactory contentFactory) {
        super(sidePaneType.getTitle());
        this.sidePaneType = sidePaneType;
        setGraphic(sidePaneType.getIcon().getGraphicNode());
        setTooltip(new Tooltip(sidePaneType.getTitle()));
        container.getStyleClass().add("sidePaneComponent");
        container.setCenter(contentFactory.create(sidePaneType));
        setContent(container);
        // The visible panes live in the StateManager; the SidePane mirrors that list, so closing goes through it
        // instead of letting the TabPane drop the tab itself.
        setOnCloseRequest(event -> {
            event.consume();
            closeCommand.execute();
        });
    }

    public SidePaneType getSidePaneType() {
        return sidePaneType;
    }

    protected void setToolbar(Node toolbar) {
        container.setTop(toolbar);
    }

    protected BorderPane getContainer() {
        return container;
    }

    public void requestFocus() {
        if (container.getCenter() instanceof GroupTreeView groupTreeView) {
            groupTreeView.requestFocusGroupTree();
        }
    }
}
