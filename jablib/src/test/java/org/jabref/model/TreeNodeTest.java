package org.jabref.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jabref.support.TreeNodeTestData;
import org.jabref.support.TreeNodeTestData.TreeNodeMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TreeNodeTest {

    Consumer<TreeNodeMock> subscriber;

    @BeforeEach
    void setUp() {
        subscriber = mock(Consumer.class);
    }

    @Test
    void constructorChecksThatClassImplementsCorrectInterface() {
        assertThrows(UnsupportedOperationException.class, WrongTreeNodeImplementation::new);
    }

    @Test
    void constructorExceptsCorrectImplementation() {
        TreeNodeMock treeNode = new TreeNodeMock();
        assertNotNull(treeNode);
    }

    @Test
    void newTreeNodeHasNoParentOrChildren() {
        TreeNodeMock treeNode = new TreeNodeMock();
        assertEquals(Optional.empty(), treeNode.getParent());
        assertEquals(List.of(), treeNode.getChildren());
        assertNotNull(treeNode);
    }

    @Test
    void getIndexedPathFromRootReturnsEmptyListForRoot() {
        TreeNodeMock root = new TreeNodeMock();
        assertEquals(List.of(), root.getIndexedPathFromRoot());
    }

    @Test
    void getIndexedPathFromRootSimplePath() {
        assertEquals(Arrays.asList(1, 0),
                TreeNodeTestData.getNodeInSimpleTree().getIndexedPathFromRoot());
    }

    @Test
    void getIndexedPathFromRootComplexPath() {
        assertEquals(Arrays.asList(2, 1, 0),
                TreeNodeTestData.getNodeInComplexTree().getIndexedPathFromRoot());
    }

    @Test
    void getDescendantSimplePath() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInSimpleTree(root);
        assertEquals(node, root.getDescendant(Arrays.asList(1, 0)).get());
    }

    @Test
    void getDescendantComplexPath() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);
        assertEquals(node, root.getDescendant(Arrays.asList(2, 1, 0)).get());
    }

    @Test
    void getDescendantNonExistentReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeTestData.getNodeInComplexTree(root);
        assertEquals(Optional.empty(), root.getDescendant(Arrays.asList(1, 100, 0)));
    }

    @Test
    void getPositionInParentForRootThrowsException() {
        TreeNodeMock root = new TreeNodeMock();
        assertThrows(UnsupportedOperationException.class, root::getPositionInParent);
    }

    @Test
    void getPositionInParentSimpleTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);
        assertEquals(2, node.getPositionInParent());
    }

    @Test
    void getIndexOfNonExistentChildReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        assertEquals(Optional.empty(), root.getIndexOfChild(new TreeNodeMock()));
    }

    @Test
    void getIndexOfChild() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);
        assertEquals((Integer) 2,
                root.getIndexOfChild(node).get());
    }

    @Test
    void getLevelOfRoot() {
        TreeNodeMock root = new TreeNodeMock();
        assertEquals(0, root.getLevel());
    }

    @Test
    void getLevelInSimpleTree() {
        assertEquals(2, TreeNodeTestData.getNodeInSimpleTree()
                .getLevel());
    }

    @Test
    void getLevelInComplexTree() {
        assertEquals(3, TreeNodeTestData.getNodeInComplexTree()
                .getLevel());
    }

    @Test
    void getChildCountInSimpleTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeTestData.getNodeInSimpleTree(root);
        assertEquals(2, root.getNumberOfChildren());
    }

    @Test
    void getChildCountInComplexTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeTestData.getNodeInComplexTree(root);
        assertEquals(4, root.getNumberOfChildren());
    }

    @Test
    void moveToAddsAsLastChildInSimpleTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInSimpleTree(root);
        node.moveTo(root);
        assertEquals((Integer) 2,
                root.getIndexOfChild(node).get());
    }

    @Test
    void moveToAddsAsLastChildInComplexTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);
        node.moveTo(root);
        assertEquals((Integer) 4,
                root.getIndexOfChild(node).get());
    }

    @Test
    void moveToChangesParent() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInSimpleTree(root);
        node.moveTo(root);
        assertEquals(root, node.getParent().get());
    }

    @Test
    void moveToInSameLevelAddsAtEnd() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock child1 = new TreeNodeMock();
        TreeNodeMock child2 = new TreeNodeMock();
        root.addChild(child1);
        root.addChild(child2);

        child1.moveTo(root);

        assertEquals(Arrays.asList(child2, child1), root.getChildren());
    }

    @Test
    void moveToInSameLevelWhenNodeWasBeforeTargetIndex() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock child1 = new TreeNodeMock();
        TreeNodeMock child2 = new TreeNodeMock();
        TreeNodeMock child3 = new TreeNodeMock();
        root.addChild(child1);
        root.addChild(child2);
        root.addChild(child3);

        child1.moveTo(root, 1);

        assertEquals(Arrays.asList(child2, child1, child3), root.getChildren());
    }

    @Test
    void moveToInSameLevelWhenNodeWasAfterTargetIndex() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock child1 = new TreeNodeMock();
        TreeNodeMock child2 = new TreeNodeMock();
        TreeNodeMock child3 = new TreeNodeMock();
        root.addChild(child1);
        root.addChild(child2);
        root.addChild(child3);

        child3.moveTo(root, 1);

        assertEquals(Arrays.asList(child1, child3, child2), root.getChildren());
    }

    @Test
    void getPathFromRootInSimpleTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInSimpleTree(root);

        List<TreeNodeMock> path = node.getPathFromRoot();
        assertEquals(3, path.size());
        assertEquals(root, path.getFirst());
        assertEquals(node, path.get(2));
    }

    @Test
    void getPathFromRootInComplexTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);

        List<TreeNodeMock> path = node.getPathFromRoot();
        assertEquals(4, path.size());
        assertEquals(root, path.getFirst());
        assertEquals(node, path.get(3));
    }

    @Test
    void getPreviousSiblingReturnsCorrect() {
        TreeNodeMock root = new TreeNodeMock();
        root.addChild(new TreeNodeMock());
        TreeNodeMock previous = new TreeNodeMock();
        root.addChild(previous);
        TreeNodeMock node = new TreeNodeMock();
        root.addChild(node);
        root.addChild(new TreeNodeMock());

        assertEquals(previous, node.getPreviousSibling().get());
    }

    @Test
    void getPreviousSiblingForRootReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        assertEquals(Optional.empty(), root.getPreviousSibling());
    }

    @Test
    void getPreviousSiblingForNonexistentReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = new TreeNodeMock();
        root.addChild(node);
        assertEquals(Optional.empty(), node.getPreviousSibling());
    }

    @Test
    void getNextSiblingReturnsCorrect() {
        TreeNodeMock root = new TreeNodeMock();
        root.addChild(new TreeNodeMock());
        TreeNodeMock node = new TreeNodeMock();
        root.addChild(node);
        TreeNodeMock next = new TreeNodeMock();
        root.addChild(next);
        root.addChild(new TreeNodeMock());

        assertEquals(next, node.getNextSibling().get());
    }

    @Test
    void getNextSiblingForRootReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        assertEquals(Optional.empty(), root.getNextSibling());
    }

    @Test
    void getNextSiblingForNonexistentReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = new TreeNodeMock();
        root.addChild(node);
        assertEquals(Optional.empty(), node.getPreviousSibling());
    }

    @Test
    void getParentReturnsCorrect() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        assertEquals(root, node.getParent().get());
    }

    @Test
    void getParentForRootReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        assertEquals(Optional.empty(), root.getParent());
    }

    @Test
    void getChildAtReturnsCorrect() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        assertEquals(node, root.getChildAt(2).get());
    }

    @Test
    void getChildAtInvalidIndexReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        root.addChild(new TreeNodeMock());
        root.addChild(new TreeNodeMock());
        assertEquals(Optional.empty(), root.getChildAt(10));
    }

    @Test
    void getRootReturnsTrueForRoot() {
        TreeNodeMock root = new TreeNodeMock();
        assertTrue(root.isRoot());
    }

    @Test
    void getRootReturnsFalseForChild() {
        assertFalse(TreeNodeTestData.getNodeInSimpleTree()
                .isRoot());
    }

    @Test
    void nodeIsAncestorOfItself() {
        TreeNodeMock root = new TreeNodeMock();
        assertTrue(root.isAncestorOf(root));
    }

    @Test
    void isAncestorOfInSimpleTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInSimpleTree(root);
        assertTrue(root.isAncestorOf(node));
    }

    @Test
    void isAncestorOfInComplexTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);
        assertTrue(root.isAncestorOf(node));
    }

    @Test
    void getRootOfSingleNode() {
        TreeNodeMock root = new TreeNodeMock();
        assertEquals(root, root.getRoot());
    }

    @Test
    void getRootInSimpleTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInSimpleTree(root);
        assertEquals(root, node.getRoot());
    }

    @Test
    void getRootInComplexTree() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);
        assertEquals(root, node.getRoot());
    }

    @Test
    void isLeafIsCorrectForRootWithoutChildren() {
        TreeNodeMock root = new TreeNodeMock();
        assertTrue(root.isLeaf());
    }

    @Test
    void removeFromParentSetsParentToEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        node.removeFromParent();
        assertEquals(Optional.empty(), node.getParent());
    }

    @Test
    void removeFromParentRemovesNodeFromChildrenCollection() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        node.removeFromParent();
        assertFalse(root.getChildren().contains(node));
    }

    @Test
    void removeAllChildrenSetsParentOfChildToEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        root.removeAllChildren();
        assertEquals(Optional.empty(), node.getParent());
    }

    @Test
    void removeAllChildrenRemovesAllNodesFromChildrenCollection() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeTestData.getNodeAsChild(root);

        root.removeAllChildren();
        assertEquals(List.of(), root.getChildren());
    }

    @Test
    void getFirstChildAtReturnsCorrect() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = new TreeNodeMock();
        root.addChild(node);

        assertEquals(node, root.getFirstChild().get());
    }

    @Test
    void getFirstChildAtLeafReturnsEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock leaf = TreeNodeTestData.getNodeAsChild(root);
        assertEquals(Optional.empty(), leaf.getFirstChild());
    }

    @Test
    void isNodeDescendantInFirstLevel() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock child = TreeNodeTestData.getNodeAsChild(root);
        assertTrue(root.isNodeDescendant(child));
    }

    @Test
    void isNodeDescendantInComplex() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock descendant = TreeNodeTestData.getNodeInComplexTree(root);
        assertTrue(root.isNodeDescendant(descendant));
    }

    @Test
    void getChildrenReturnsAllChildren() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock child1 = new TreeNodeMock();
        TreeNodeMock child2 = new TreeNodeMock();
        root.addChild(child1);
        root.addChild(child2);

        assertEquals(Arrays.asList(child1, child2), root.getChildren());
    }

    @Test
    void removeChildSetsParentToEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        root.removeChild(node);
        assertEquals(Optional.empty(), node.getParent());
    }

    @Test
    void removeChildRemovesNodeFromChildrenCollection() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        root.removeChild(node);
        assertFalse(root.getChildren().contains(node));
    }

    @Test
    void removeChildIndexSetsParentToEmpty() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        root.removeChild(2);
        assertEquals(Optional.empty(), node.getParent());
    }

    @Test
    void removeChildIndexRemovesNodeFromChildrenCollection() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        root.removeChild(2);
        assertFalse(root.getChildren().contains(node));
    }

    @Test
    void addThrowsExceptionIfNodeHasParent() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);
        assertThrows(UnsupportedOperationException.class, () -> root.addChild(node));
    }

    @Test
    void moveAllChildrenToAddsAtSpecifiedPosition() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = new TreeNodeMock();
        root.addChild(node);
        TreeNodeMock child1 = new TreeNodeMock();
        TreeNodeMock child2 = new TreeNodeMock();
        node.addChild(child1);
        node.addChild(child2);

        node.moveAllChildrenTo(root, 0);
        assertEquals(Arrays.asList(child1, child2, node), root.getChildren());
    }

    @Test
    void moveAllChildrenToChangesParent() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = new TreeNodeMock();
        root.addChild(node);
        TreeNodeMock child1 = new TreeNodeMock();
        TreeNodeMock child2 = new TreeNodeMock();
        node.addChild(child1);
        node.addChild(child2);

        node.moveAllChildrenTo(root, 0);
        assertEquals(root, child1.getParent().get());
        assertEquals(root, child2.getParent().get());
    }

    @Test
    void moveAllChildrenToDescendantThrowsException() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        assertThrows(UnsupportedOperationException.class, () -> root.moveAllChildrenTo(node, 0));
    }

    @Test
    void sortChildrenSortsInFirstLevel() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock child1 = new TreeNodeMock("a");
        TreeNodeMock child2 = new TreeNodeMock("b");
        TreeNodeMock child3 = new TreeNodeMock("c");
        root.addChild(child2);
        root.addChild(child3);
        root.addChild(child1);

        root.sortChildren((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()),
                false);
        assertEquals(Arrays.asList(child1, child2, child3), root.getChildren());
    }

    @Test
    void sortChildrenRecursiveSortsInDeeperLevel() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInSimpleTree(root);
        TreeNodeMock child1 = new TreeNodeMock("a");
        TreeNodeMock child2 = new TreeNodeMock("b");
        TreeNodeMock child3 = new TreeNodeMock("c");
        node.addChild(child2);
        node.addChild(child3);
        node.addChild(child1);

        root.sortChildren((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()),
                true);
        assertEquals(Arrays.asList(child1, child2, child3), node.getChildren());
    }

    @Test
    void copySubtreeCopiesChildren() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeAsChild(root);

        TreeNodeMock copiedRoot = root.copySubtree();
        assertEquals(Optional.empty(), copiedRoot.getParent());
        assertFalse(copiedRoot.getChildren().contains(node));
        assertEquals(root.getNumberOfChildren(), copiedRoot.getNumberOfChildren());
    }

    @Test
    void addChildSomewhereInTreeInvokesChangeEvent() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);

        root.subscribeToDescendantChanged(subscriber);

        node.addChild(new TreeNodeMock());
        verify(subscriber).accept(node);
    }

    @Test
    void moveNodeSomewhereInTreeInvokesChangeEvent() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);
        TreeNodeMock oldParent = node.getParent().get();

        root.subscribeToDescendantChanged(subscriber);

        node.moveTo(root);
        verify(subscriber).accept(root);
        verify(subscriber).accept(oldParent);
    }

    @Test
    void removeChildSomewhereInTreeInvokesChangeEvent() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);
        TreeNodeMock child = node.addChild(new TreeNodeMock());

        root.subscribeToDescendantChanged(subscriber);

        node.removeChild(child);
        verify(subscriber).accept(node);
    }

    @Test
    void removeChildIndexSomewhereInTreeInvokesChangeEvent() {
        TreeNodeMock root = new TreeNodeMock();
        TreeNodeMock node = TreeNodeTestData.getNodeInComplexTree(root);
        node.addChild(new TreeNodeMock());

        root.subscribeToDescendantChanged(subscriber);

        node.removeChild(0);
        verify(subscriber).accept(node);
    }

    @Test
    void findChildrenWithSameName() {
        TreeNodeMock root = new TreeNodeMock("A");
        TreeNodeMock childB = root.addChild(new TreeNodeMock("B"));
        TreeNodeMock node = childB.addChild(new TreeNodeMock("A"));
        TreeNodeMock childA = root.addChild(new TreeNodeMock("A"));

        assertEquals(Arrays.asList(root, node, childA),
                root.findChildrenSatisfying(treeNode -> "A".equals(treeNode.getName())));
    }

    private static class WrongTreeNodeImplementation extends TreeNode<TreeNodeMock> {

        // This class is a wrong derived class of TreeNode<T>
        // since it does not extends TreeNode<WrongTreeNodeImplementation>
        // See test constructorChecksThatClassImplementsCorrectInterface
        public WrongTreeNodeImplementation() {
            super(TreeNodeMock.class);
        }

        @Override
        public TreeNodeMock copyNode() {
            return null;
        }
    }
}
