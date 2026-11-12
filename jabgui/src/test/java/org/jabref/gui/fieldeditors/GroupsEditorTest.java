package org.jabref.gui.fieldeditors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GroupsEditorTest {
    @Test
    void extractGroupNameFromHierarchicalPath() {
        assertEquals("child", GroupsEditor.extractGroupName("parent > child"));
    }

    @Test
    void extractGroupNameFromDeeplyNestedPath() {
        assertEquals("child", GroupsEditor.extractGroupName("root > parent > child"));
    }

    @Test
    void extractGroupNameFromSimpleName() {
        assertEquals("mygroup", GroupsEditor.extractGroupName("mygroup"));
    }
}
