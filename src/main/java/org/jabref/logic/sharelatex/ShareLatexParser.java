package org.jabref.logic.sharelatex;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.ParseException;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.importer.fileformat.BibtexParser;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.sharelatex.ShareLatexOtAppliedMessage;
import org.jabref.model.sharelatex.ShareLatexProject;
import org.jabref.model.util.FileUpdateMonitor;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShareLatexParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareLatexParser.class);

    private final JsonParser parser = new JsonParser();
    private final Gson gson = new GsonBuilder().create();

    public int getVersionFromBibTexJsonString(String content) {
        JsonArray array = parseFirstPartOfMessageAsArray(content);
        return array.get(2).getAsInt();
    }

    public ShareLatexOtAppliedMessage getOtAppliedMessage(String content) {
        String strs = content.substring(content.indexOf("{"), content.length());
        ShareLatexOtAppliedMessage message = gson.fromJson(strs, ShareLatexOtAppliedMessage.class);
        return message;
    }

    public int getPositionFromBibtexJsonUpdateMessage(String content) {
        String strs = content.substring(content.indexOf("{"), content.length());

        ShareLatexOtAppliedMessage message = gson.fromJson(strs, ShareLatexOtAppliedMessage.class);
        return message.getArgs().get(0).getOp().get(0).getPosition();

        //   5:::{"name":"otUpdateApplied","args":[{"doc":"5a797ca3b42d76683b3ea200","op":[{"p":633,"d":"A. Viterbi"}],"v":71,"meta":{"source":"x3f_9gg_sYE1IC9v_oTa","user_id":"5a797c98b42d76683b3ea1fc","ts":1517997414640}}]}
    }

    private JsonObject getFirstEntryOfArrayAsJsonObject(JsonObject obj, String arrName) {
        return obj.get(arrName).getAsJsonArray().get(0).getAsJsonObject();
    }

    public List<BibEntry> parseBibEntryFromJsonMessageString(String message, ImportFormatPreferences prefs, FileUpdateMonitor fileMonitor)
            throws ParseException {
        return parseBibEntryFromJsonArray(parseFirstPartOfMessageAsArray(message), prefs, fileMonitor);
    }

    public String getBibTexStringFromJsonMessage(String message) {
        return getBibTexStringFromJsonArray(parseFirstPartOfMessageAsArray(message));
    }

    public String getOtErrorMessageContent(String otUpdateError) {
        JsonObject obj = parseFirstPartOfMessageAsObject(otUpdateError);
        return obj.get("args").getAsJsonArray().get(0).getAsString();
    }

    public String getFirstBibTexDatabaseId(String json) {
        JsonObject obj = parseFirstPartOfMessageAsArray(json).get(1).getAsJsonObject();
        JsonArray arr = obj.get("rootFolder").getAsJsonArray();

        Optional<JsonArray> docs = arr.get(0)
                                      .getAsJsonObject()
                                      .entrySet()
                                      .stream()
                                      .filter(entry -> entry.getKey().equals("docs"))
                                      .map(v -> v.getValue().getAsJsonArray())
                                      .findFirst();

        if (docs.isPresent()) {
            JsonArray jsonArray = docs.get();
            for (JsonElement doc : jsonArray) {
                String name = doc.getAsJsonObject().get("name").getAsString();
                String id = doc.getAsJsonObject().get("_id").getAsString();

                if (name.endsWith(".bib")) {
                    return id;
                }
            }
        }
        return "";
    }

    public List<SharelatexDoc> generateDiffs(String before, String after) {
        Patch<String> patches;
        // Splits the lines using "\n" - therefore, we can use "\n" later on to join the text again
        patches = DiffUtils.diff(before, after, null);

        int pos = 0;

        List<SharelatexDoc> docsWithChanges = new ArrayList<>();

        for (AbstractDelta<String> delta : patches.getDeltas()) {
            SharelatexDoc doc = new SharelatexDoc();
            String newText;
            String deletedText;
            switch (delta.getType()) {
                case EQUAL:
                    pos += delta.getSource().size();
                    break;
                case INSERT:
                    doc.setPosition(pos);
                    newText = delta.getTarget().getLines().stream().collect(Collectors.joining("\n"));
                    doc.setContent(newText);
                    doc.setOperation("i");
                    docsWithChanges.add(doc);
                    pos += newText.length();
                    break;
                case DELETE:
                    doc.setPosition(pos);
                    deletedText = delta.getSource().getLines().stream().collect(Collectors.joining("\n"));
                    doc.setContent(deletedText);
                    doc.setOperation("d");
                    docsWithChanges.add(doc);
                    break;
                case CHANGE:
                    // CHANGE is delete and insert

                    doc.setPosition(pos);
                    deletedText = delta.getSource().getLines().stream().collect(Collectors.joining("\n"));
                    doc.setContent(deletedText);
                    doc.setOperation("d");
                    docsWithChanges.add(doc);

                    doc.setPosition(pos);
                    newText = delta.getTarget().getLines().stream().collect(Collectors.joining("\n"));
                    doc.setContent(newText);
                    doc.setOperation("i");
                    docsWithChanges.add(doc);
                    pos += newText.length();
                    break;
                default:
                    LOGGER.error("Unknown delta type");
                    break;
            }
        }
        return docsWithChanges;
    }

    public List<ShareLatexProject> getProjectFromJson(JsonObject json) {
        List<ShareLatexProject> projects = new ArrayList<>();
        if (json.has("projects")) {
            JsonArray projectArray = json.get("projects").getAsJsonArray();
            for (JsonElement elem : projectArray) {
                String id = elem.getAsJsonObject().get("id").getAsString();
                String name = elem.getAsJsonObject().get("name").getAsString();
                String lastUpdated = elem.getAsJsonObject().get("lastUpdated").getAsString();
                // String owner = elem.getAsJsonObject().get("owner_ref").getAsString();

                JsonObject owner = elem.getAsJsonObject().get("owner").getAsJsonObject();
                String firstName = owner.get("first_name").getAsString();
                String lastName = owner.get("email").getAsString();

                ShareLatexProject project = new ShareLatexProject(id, name, firstName, lastName, lastUpdated);
                projects.add(project);
            }
        }
        return projects;
    }

    private List<BibEntry> parseBibEntryFromJsonArray(JsonArray arr, ImportFormatPreferences prefs, FileUpdateMonitor fileMonitor)
            throws ParseException {
        String bibtexString = getBibTexStringFromJsonArray(arr);
        BibtexParser parser = new BibtexParser(prefs, fileMonitor);
        return parser.parseEntries(bibtexString);
    }

    private JsonArray parseFirstPartOfMessageAsArray(String documentToParse) {
        String jsonToRead = documentToParse.substring(documentToParse.indexOf("+") + 1, documentToParse.length());
        JsonArray arr = parser.parse(jsonToRead).getAsJsonArray();
        return arr;
    }

    private JsonObject parseFirstPartOfMessageAsObject(String documentToParse) {
        String jsonToRead = documentToParse.substring(documentToParse.indexOf("{"), documentToParse.length());
        return parser.parse(jsonToRead).getAsJsonObject();
    }

    private String getBibTexStringFromJsonArray(JsonArray arr) {
        JsonArray stringArr = arr.get(1).getAsJsonArray();

        StringJoiner joiner = new StringJoiner("\n");

        for (JsonElement elem : stringArr) {
            joiner.add(elem.getAsString());
        }

        return joiner.toString();
    }

    /**
     * Fixes wrongly encoded UTF-8 strings which were encoded into ISO-8859-1 Workaround for server side bug
     *
     * @param wrongEncoded The wrongly encoded string
     * @return The correct UTF-8 string
     */
    public String fixUTF8Strings(String wrongEncoded) {
        String str = new String(wrongEncoded.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        return new String(str.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    public Optional<BibEntry> getEntryFromPosition(ParserResult result, int position) {
        Objects.requireNonNull(result);
        if (position < 1) {
            throw new IllegalArgumentException("Position must be positive");
        }

        int currentStartPos = 0;
        for (BibEntry entry : result.getDatabase().getEntries()) {
            int endPos = currentStartPos + entry.getParsedSerialization().length();
            boolean isInRange = (currentStartPos <= position) && (position <= endPos);
            if (isInRange) {
                return Optional.of(entry);
            } else {
                currentStartPos = endPos;
            }
        }

        return Optional.empty();
    }
}
