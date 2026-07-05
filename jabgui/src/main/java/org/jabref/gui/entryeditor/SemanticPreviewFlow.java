package org.jabref.gui.entryeditor;

import java.util.Optional;
import java.util.function.Consumer;

import javafx.scene.Cursor;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldTextMapper;

import org.jspecify.annotations.NullMarked;

/// Renders the [CitationSegments] of an entry as a wrapping text flow — the visual
/// part of the editable semantic preview in the [AllFieldsTab] (issue #12711,
/// concept #2). Field values and `{{placeholders}}` are clickable segments (the
/// click callback opens the field's editor); punctuation is inert text.
// [impl->req~entry-editor.main-tab.semantic-preview~1]
@NullMarked
public class SemanticPreviewFlow extends TextFlow {

    public SemanticPreviewFlow() {
        getStyleClass().add("semantic-preview-flow");
    }

    /// Renders the tokens; the segments of `editingField` (currently edited in place)
    /// get an extra highlight style.
    public void render(CitationSegments segments, Optional<Field> editingField, Consumer<Field> onFieldClick) {
        getChildren().clear();
        for (CitationSegments.Token token : segments.tokens()) {
            switch (token) {
                case CitationSegments.TextToken(String text) -> {
                    Text textNode = new Text(text);
                    textNode.getStyleClass().add("semantic-preview-text");
                    getChildren().add(textNode);
                }
                case CitationSegments.FieldToken(Field field, String displayText, CitationSegments.SegmentStyle style) -> {
                    Text textNode = new Text(displayText);
                    textNode.getStyleClass().add("semantic-preview-value");
                    if (style == CitationSegments.SegmentStyle.ITALIC) {
                        textNode.getStyleClass().add("semantic-preview-italic");
                    }
                    if (editingField.filter(field::equals).isPresent()) {
                        textNode.getStyleClass().add("semantic-preview-editing");
                    }
                    addClickableSegment(textNode, field, onFieldClick);
                }
                case CitationSegments.PlaceholderToken(Field field) -> {
                    Text textNode = new Text("{{" + FieldTextMapper.getDisplayName(field) + "}}");
                    textNode.getStyleClass().add("semantic-preview-placeholder");
                    if (editingField.filter(field::equals).isPresent()) {
                        textNode.getStyleClass().add("semantic-preview-editing");
                    }
                    addClickableSegment(textNode, field, onFieldClick);
                }
            }
        }
    }

    private void addClickableSegment(Text textNode, Field field, Consumer<Field> onFieldClick) {
        textNode.setCursor(Cursor.HAND);
        Tooltip.install(textNode, new Tooltip(FieldTextMapper.getDisplayName(field)));
        textNode.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                onFieldClick.accept(field);
            }
        });
        getChildren().add(textNode);
    }
}
