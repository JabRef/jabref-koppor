package org.jabref.model.openoffice;

import com.sun.star.text.XTextRange;

/**
 * A simple implementation of {@code RangeSortable}
 */
public class RangeSortEntry<T> implements RangeSortable<T> {

    private XTextRange range;
    private int indexInPosition;
    private T content;

    public RangeSortEntry(XTextRange range, int indexInPosition, T content) {
        this.range = range;
        this.indexInPosition = indexInPosition;
        this.content = content;
    }

    @Override
    public XTextRange getRange() {
        return range;
    }

    @Override
    public int getIndexInPosition() {
        return indexInPosition;
    }

    @Override
    public T getContent() {
        return content;
    }

    public void setRange(XTextRange r) {
        range = r;
    }

    public void setIndexInPosition(int i) {
        indexInPosition = i;
    }
}
