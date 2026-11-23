package org.jabref.languageserver.util;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jabref.logic.citationstyle.CSLStyleLoader;
import org.jabref.logic.citationstyle.CSLStyleUtils;
import org.jabref.logic.citationstyle.CitationStyleGenerator;
import org.jabref.logic.citationstyle.CitationStyleOutputFormat;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.preferences.JabRefCliPreferences;
import org.jabref.logic.preview.BstPreviewLayout;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;

import org.jspecify.annotations.NullMarked;

/// Renders an entry as Markdown citation in the preview style selected in JabRef.
/// The customized preview style is skipped: it is a JabRef layout written for the HTML preview panel.
/// Then the first CSL or BST style of the preview cycle is used, and JabRef's default CSL style if there is none.
// [impl->feat~fetchers.language-server-hover-citation~1]
@NullMarked
public class LspCitationRenderer {

    // Keys of JabRefGuiPreferences, which lives in jabgui. JabRef writes them to the preference store shared with the
    // standalone server, so both the embedded and the standalone server follow the style selected in JabRef.
    private static final String PREVIEW_BST_LAYOUT_PATHS = "previewBstLayoutPaths";
    private static final String PREVIEW_CYCLE = "cyclePreview";
    private static final String PREVIEW_CYCLE_POS = "cyclePreviewPos";

    // BstPreviewLayout emits these tags; everything else is plain text
    private static final Pattern BOLD_TAG = Pattern.compile("</?b>");
    private static final Pattern ITALIC_TAG = Pattern.compile("</?(i|em)>");
    private static final Pattern OTHER_TAG = Pattern.compile("<[^>]+>");
    // citeproc's Markdown output glues a numeric label to the text (`\[1\]K\. A\. Cooper`) and ends with `<br />`
    private static final Pattern CSL_LABEL = Pattern.compile("^(\\\\\\[[^\\]]*\\\\\\])(?=\\S)");

    private final CliPreferences preferences;
    private final BibEntryTypesManager entryTypesManager;

    public LspCitationRenderer(CliPreferences preferences, BibEntryTypesManager entryTypesManager) {
        this.preferences = preferences;
        this.entryTypesManager = entryTypesManager;
    }

    public String render(BibEntry entry) {
        BibDatabaseContext context = new BibDatabaseContext();
        return styleCandidates().stream()
                                .map(style -> render(entry, style, context))
                                .flatMap(Optional::stream)
                                .findFirst()
                                .orElse("");
    }

    /// The selected style first, then the whole cycle, then the default style.
    List<String> styleCandidates() {
        if (!(preferences instanceof JabRefCliPreferences store)) {
            return List.of(CSLStyleLoader.DEFAULT_STYLE);
        }
        List<String> cycle = store.getStringList(PREVIEW_CYCLE);
        int position = store.getInt(PREVIEW_CYCLE_POS, 0);
        Stream<String> selected = position >= 0 && position < cycle.size() ? Stream.of(cycle.get(position)) : Stream.empty();
        return Stream.of(selected, cycle.stream(), Stream.of(CSLStyleLoader.DEFAULT_STYLE))
                     .flatMap(styles -> styles)
                     .toList();
    }

    private Optional<String> render(BibEntry entry, String style, BibDatabaseContext context) {
        if (CSLStyleUtils.isCitationStyleFile(style)) {
            return CSLStyleUtils.createCitationStyleFromFile(style)
                                .map(citationStyle -> String.join("\n\n", CitationStyleGenerator.generateBibliography(
                                        List.of(entry), citationStyle.getSource(), CitationStyleOutputFormat.MARKDOWN, context, entryTypesManager)))
                                .map(markdown -> CSL_LABEL.matcher(OTHER_TAG.matcher(markdown).replaceAll("").trim()).replaceFirst("$1 "));
        }
        if (BstPreviewLayout.isBstStyleFile(style) && preferences instanceof JabRefCliPreferences store) {
            return store.getStringList(PREVIEW_BST_LAYOUT_PATHS).stream()
                        .map(Path::of)
                        .filter(path -> path.endsWith(style))
                        .findFirst()
                        .map(path -> htmlToMarkdown(new BstPreviewLayout(path).generatePreview(entry, context)));
        }
        return Optional.empty();
    }

    static String htmlToMarkdown(String html) {
        String markdown = BOLD_TAG.matcher(html).replaceAll("**");
        markdown = ITALIC_TAG.matcher(markdown).replaceAll("*");
        return OTHER_TAG.matcher(markdown).replaceAll("");
    }
}
