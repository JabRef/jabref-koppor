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
import org.jabref.logic.formatter.bibtexfields.HtmlToUnicodeFormatter;
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

    // BstPreviewLayout and citeproc emit these tags; everything else becomes plain text
    private static final Pattern BOLD_TAG = Pattern.compile("</?b>");
    private static final Pattern ITALIC_TAG = Pattern.compile("</?(i|em)>");
    private static final Pattern ITALIC_SPAN = Pattern.compile("<span style=\"font-style:\\s*italic;?\">(.*?)</span>");
    private static final Pattern BOLD_SPAN = Pattern.compile("<span style=\"font-weight:\\s*bold;?\">(.*?)</span>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LEADING_LABEL = Pattern.compile("^\\[[^\\]]*\\]\\s*");
    // citeproc puts a numeric label into its own HTML block, so without the tags it is glued to the text (`[1]K. A. Cooper`)
    private static final Pattern CSL_LABEL = Pattern.compile("^(\\[[^\\]]*\\])(?=\\S)");

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

    /// The citation for running text: without a leading label such as `\[1\]`, which numbers a bibliography list
    public String renderInline(BibEntry entry) {
        return LEADING_LABEL.matcher(render(entry)).replaceFirst("");
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
                                // HTML instead of citeproc's Markdown, which escapes every punctuation mark (`K\\. A\\. Cooper\\,`)
                                .map(citationStyle -> String.join(" ", CitationStyleGenerator.generateBibliography(
                                        List.of(entry), citationStyle.getSource(), CitationStyleOutputFormat.HTML, context, entryTypesManager)))
                                .map(html -> CSL_LABEL.matcher(htmlToMarkdown(html)).replaceFirst("$1 "));
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
        String markdown = ITALIC_SPAN.matcher(html).replaceAll("*$1*");
        markdown = BOLD_SPAN.matcher(markdown).replaceAll("**$1**");
        markdown = BOLD_TAG.matcher(markdown).replaceAll("**");
        markdown = ITALIC_TAG.matcher(markdown).replaceAll("*");
        markdown = new HtmlToUnicodeFormatter().format(markdown);
        return WHITESPACE.matcher(markdown).replaceAll(" ").trim();
    }
}
