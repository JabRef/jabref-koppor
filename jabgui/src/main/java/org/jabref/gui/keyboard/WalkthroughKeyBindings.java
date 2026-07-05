package org.jabref.gui.keyboard;

import javafx.scene.input.KeyEvent;

import org.jabref.gui.StateManager;

public class WalkthroughKeyBindings {

    /// Handles ESC key to quit active walkthrough with confirmation.
    ///
    /// @param event                the key event
    /// @param stateManager         the state manager
    /// @param keyBindingRepository the key binding repository
    public static void call(KeyEvent event, StateManager stateManager, KeyBindingRepository keyBindingRepository) {
        keyBindingRepository.mapToKeyBinding(event).ifPresent(binding -> {
            if (binding == KeyBinding.CLOSE) { // NOTE: CLOSE is using Esc key. Therefore, we didn't introduce a new key binding entry since this would lead to conflicts with other key bindings.
                // Only consume the event when a walkthrough is actually active — this filter
                // sits on the main scene, so consuming unconditionally would swallow Esc for
                // the entire application (dialogs, entry editor, ...).
                stateManager.getActiveWalkthrough().ifPresent(walkthrough -> {
                    walkthrough.showQuitConfirmationAndQuit();
                    event.consume();
                });
            }
        });
    }
}
