package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.sun.star.text.XText;
import com.sun.star.text.XTextRange;

/**
 * Purpose: in order to check overlaps of XTextRange values, sort
 *          them, and allow recovering some corresponding information
 *          (of type V)
 *
 * Since XTextRange values are only comparable if they share the same
 * r.getText(), we group them by these.
 *
 * Within such groups (partitions) we may define comparison, here
 * based on (r.getStart(),r.getEnd()), thus equality means identical
 * ranges.
 *
 * For finding overlapping ranges this class proved insufficient,
 * beacause it does not allow multiple values to be associated with a
 * single XTextRange.  The class RangeKeyedMapList solves this.
 *
 */
public class RangeKeyedMap<V> {
    private final Map<XText, TreeMap<XTextRange, V>> xxs;

    public RangeKeyedMap() {
        this.xxs = new HashMap<>();
    }

    public boolean containsKey(XTextRange r) {
        Objects.requireNonNull(r);
        XText partitionKey = r.getText();
        if (!xxs.containsKey(partitionKey)) {
            return false;
        }
        return xxs.get(partitionKey).containsKey(r);
    }

    public V get(XTextRange r) {
        TreeMap<XTextRange, V> xs = xxs.get(r.getText());
        if (xs == null) {
            return null;
        }
        return xs.get(r);
    }

    private static int comparator(XTextRange a, XTextRange b) {
        int startOrder = UnoTextRange.compareStarts(a, b);
        if (startOrder != 0) {
            return startOrder;
        }
        return UnoTextRange.compareEnds(a, b);
    }

    public V put(XTextRange r, V value) {
        TreeMap<XTextRange, V> xs = xxs.get(r.getText());
        if (xs == null) {
            xs = new TreeMap<>(RangeKeyedMap::comparator);
            xxs.put(r.getText(), xs);
        }
        return xs.put(r, value);
    }

    /**
     * @return A list of the partitions.
     */
    public List<TreeMap<XTextRange, V>> partitionValues() {
        return new ArrayList(xxs.values());
    }
}
