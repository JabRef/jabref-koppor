package org.jabref.logic.quality.consistency;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jabref.model.entry.EntryConverter;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.StandardField;

import org.jspecify.annotations.NullMarked;

/// Selects the fields a bibliography style can read, given the field names the style file mentions.
///
/// Mirrors [org.jabref.model.entry.BibEntry#getFieldOrAlias]: a style reading `journal` sees `journaltitle`,
/// a style reading `date` sees `year`, `month` and `day`, and the other way round.
@NullMarked
public class StyleFieldSelection {

    private static final Set<Field> DATE_FIELDS = Set.of(StandardField.DATE, StandardField.YEAR, StandardField.MONTH, StandardField.DAY);

    private StyleFieldSelection() {
    }

    /// @param fieldNames the lower-cased field names the style mentions
    /// @return the selection, or empty if the style mentions no field at all
    public static Optional<Predicate<Field>> forFieldNames(Set<String> fieldNames) {
        if (fieldNames.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(field -> {
            Stream<Field> candidates = DATE_FIELDS.contains(field)
                                       ? DATE_FIELDS.stream()
                                       : Stream.concat(Stream.of(field), Optional.ofNullable(EntryConverter.FIELD_ALIASES.get(field)).stream());
            return candidates.map(candidate -> candidate.getName().toLowerCase(Locale.ROOT)).anyMatch(fieldNames::contains);
        });
    }
}
