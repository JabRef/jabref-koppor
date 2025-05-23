package org.jabref.model.entry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;

import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.InternalField;

public class CanonicalBibEntry {

    private CanonicalBibEntry() {
    }

    /// This returns a canonical BibTeX serialization.
    /// The result is close to BibTeX, but not a valid BibTeX representation in all cases
    ///
    ///  - Serializes all fields, even the JabRef internal ones.
    ///  - Does NOT serialize "KEY_FIELD" as field, but as key.
    ///  - Special characters such as "{" or "&" are NOT escaped, but written as is.
    ///  - New lines are written as is.
    ///  - String constants are not handled. That means, `month = apr` in a bib file gets `month = {#apr#}`.
    ///    This indicates that the month field is correctly stored.
    ///
    public static String getCanonicalRepresentation(BibEntry entry) {
        StringBuilder sb = new StringBuilder();

        sb.append(entry.getUserComments());

        // generate first line: type and citation key
        String citeKey = entry.getCitationKey().orElse("");
        sb.append("@%s{%s,".formatted(entry.getType().getName(), citeKey)).append('\n');

        // we have to introduce a new Map as fields are stored case-sensitive in JabRef (see https://github.com/koppor/jabref/issues/45).
        Map<String, String> mapFieldToValue = new HashMap<>();

        // determine sorted fields -- all fields lower case
        SortedSet<String> sortedFields = new TreeSet<>();
        for (Entry<Field, String> field : entry.getFieldMap().entrySet()) {
            Field fieldName = field.getKey();
            String fieldValue = field.getValue();
            // JabRef stores the key in the field KEY_FIELD, which must not be serialized
            if (!fieldName.equals(InternalField.KEY_FIELD)) {
                String lowerCaseFieldName = fieldName.getName().toLowerCase(Locale.US);
                sortedFields.add(lowerCaseFieldName);
                mapFieldToValue.put(lowerCaseFieldName, fieldValue);
            }
        }

        // generate field entries
        StringJoiner sj = new StringJoiner(",\n", "", "\n");
        for (String fieldName : sortedFields) {
            String line = "  %s = {%s}".formatted(fieldName, mapFieldToValue.get(fieldName));
            sj.add(line);
        }

        sj.add("  _jabref_shared = {sharedId: %d, version: %d}".formatted(entry.getSharedBibEntryData().getSharedID(), entry.getSharedBibEntryData().getVersion()));

        sb.append(sj);

        // append the closing entry bracket
        sb.append('}');
        return sb.toString();
    }
}
