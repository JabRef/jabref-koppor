package org.jabref.gui.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import javafx.scene.paint.Color;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.util.BaseDialog;
import org.jabref.gui.util.UiTaskExecutor;
import org.jabref.logic.FilePreferences;
import org.jabref.logic.ai.AiService;
import org.jabref.logic.ai.chatting.ChatModel;
import org.jabref.logic.ai.chatting.tasks.GenerateRagResponseTask;
import org.jabref.logic.ai.ingestion.tasks.generateembeddings.GenerateEmbeddingsTaskRequest;
import org.jabref.logic.ai.preferences.AiPreferences;
import org.jabref.logic.ai.rag.logic.ResponseEngine;
import org.jabref.logic.ai.rag.util.ResponseEngineFactory;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.BackgroundTask;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.model.ai.chatting.ChatMessage;
import org.jabref.model.ai.identifiers.FullBibEntry;
import org.jabref.model.ai.pipeline.ResponseEngineKind;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;

import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jabref.gui.actions.ActionHelper.needsEntriesSelected;

/// Asks a fixed list of questions to each selected entry, one entry after the other, one question after the other.
/// Questions and answers are appended to the entry's AI chat, so they show up in the entry editor's AI chat tab.
///
/// Each question is sent without the previous questions and answers in the context, so the prompt stays small
/// (system message + retrieved excerpts + question) and works with small models.
@NullMarked
public class AskQuestionsAction extends SimpleCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AskQuestionsAction.class);
    private static final Pattern QUESTION_SEPARATOR = Pattern.compile("(?m)^\\s*---\\s*$");
    private static final Pattern SEPARATOR_LINE = Pattern.compile("\\s*---\\s*");
    private static final StyleAttributeMap EVEN_SECTION = StyleAttributeMap.builder().setBackground(Color.TRANSPARENT).build();
    private static final StyleAttributeMap ODD_SECTION = StyleAttributeMap.builder().setBackground(Color.gray(0.5, 0.15)).build();

    // ponytail: remembered per JabRef run only, add a preference if this survives the experiment
    private static String lastQuestions = "";

    private final StateManager stateManager;
    private final DialogService dialogService;
    private final AiService aiService;
    private final AiPreferences aiPreferences;
    private final FilePreferences filePreferences;
    private final TaskExecutor taskExecutor;

    public AskQuestionsAction(StateManager stateManager,
                              DialogService dialogService,
                              AiService aiService,
                              AiPreferences aiPreferences,
                              FilePreferences filePreferences,
                              TaskExecutor taskExecutor) {
        this.stateManager = stateManager;
        this.dialogService = dialogService;
        this.aiService = aiService;
        this.aiPreferences = aiPreferences;
        this.filePreferences = filePreferences;
        this.taskExecutor = taskExecutor;
        this.executable.bind(needsEntriesSelected(stateManager));
    }

    @Override
    public void execute() {
        Optional<BibDatabaseContext> context = stateManager.getActiveDatabase();
        if (context.isEmpty() || stateManager.getSelectedEntries().isEmpty()) {
            return;
        }
        if (!aiPreferences.getAiFeaturesEnabled()) {
            dialogService.notify(Localization.lang("AI features are disabled. Enable them in the preferences."));
            return;
        }

        List<BibEntry> entries = new ArrayList<>(stateManager.getSelectedEntries());
        List<String> questions = askForQuestions();
        if (questions.isEmpty()) {
            return;
        }

        BackgroundTask<Void> task = new BackgroundTask<>() {
            @Override
            public Void call() {
                askAll(this, context.get(), entries, questions);
                return null;
            }
        };
        task.showToUser(true);
        task.titleProperty().set(Localization.lang("Asking AI questions"));
        task.onFailure(ex -> LOGGER.error("Asking AI questions failed", ex));
        task.executeWith(taskExecutor);
    }

    private List<String> askForQuestions() {
        BaseDialog<String> dialog = new BaseDialog<>() {
        };
        dialog.setTitle(Localization.lang("Ask AI questions"));
        dialog.setHeaderText(Localization.lang("Each question is asked to every selected entry. Separate questions with a line containing only ---"));
        RichTextArea textArea = new RichTextArea();
        textArea.setWrapText(true);
        textArea.setPrefSize(600, 300);
        textArea.appendText(lastQuestions);
        textArea.getModel().addListener(change -> {
            if (change.isEdit()) {
                // Restyling inside the change callback would modify the model while it is being edited
                Platform.runLater(() -> colorSections(textArea));
            }
        });
        colorSections(textArea);
        dialog.getDialogPane().setContent(textArea);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? plainText(textArea) : null);

        Optional<String> text = dialogService.showCustomDialogAndWait(dialog);
        if (text.isEmpty()) {
            return List.of();
        }
        lastQuestions = text.get();
        return QUESTION_SEPARATOR.splitAsStream(text.get())
                                 .map(String::strip)
                                 .filter(question -> !question.isEmpty())
                                 .toList();
    }

    /// Alternates the paragraph background per `---` section, like the rows of the main table.
    private static void colorSections(RichTextArea textArea) {
        boolean odd = false;
        for (int i = 0; i < textArea.getParagraphCount(); i++) {
            if (SEPARATOR_LINE.matcher(textArea.getModel().getPlainText(i)).matches()) {
                odd = !odd;
            }
            textArea.applyStyle(TextPos.ofLeading(i, 0), textArea.getParagraphEnd(i), odd ? ODD_SECTION : EVEN_SECTION);
        }
    }

    private static String plainText(RichTextArea textArea) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < textArea.getParagraphCount(); i++) {
            text.append(textArea.getModel().getPlainText(i)).append('\n');
        }
        return text.toString();
    }

    private void askAll(BackgroundTask<Void> task, BibDatabaseContext context, List<BibEntry> entries, List<String> questions) {
        ChatModel chatModel = aiService.getCurrentChatModel();
        ResponseEngine responseEngine = ResponseEngineFactory.create(
                aiPreferences,
                filePreferences,
                aiService.getCurrentEmbeddingModel(),
                aiService.getEmbeddingsStore());

        int total = entries.size() * questions.size();
        int done = 0;
        task.updateProgress(done, total);

        for (BibEntry entry : entries) {
            if (task.isCancelled()) {
                return;
            }
            String entryName = entry.getCitationKey().orElse(entry.getId());
            FullBibEntry fullEntry = new FullBibEntry(context, entry);
            ObservableList<ChatMessage> chatHistory = aiService.getChatHistoryCache().getForEntry(context, entry);

            if (responseEngine.getKind() == ResponseEngineKind.EMBEDDINGS_SEARCH) {
                task.updateMessage(Localization.lang("Generating embeddings for %0", entryName));
                ingestFiles(context, entry);
            }

            for (String question : questions) {
                if (task.isCancelled()) {
                    return;
                }
                task.updateMessage(entryName + ": " + question);

                ChatMessage userMessage = ChatMessage.userMessage(question);
                ChatMessage answer;
                try {
                    answer = new GenerateRagResponseTask(
                            chatModel,
                            responseEngine,
                            List.of(userMessage),
                            List.of(fullEntry),
                            aiPreferences.getChattingSystemMessageTemplate(),
                            aiPreferences.getChattingUserMessageTemplate()
                    ).call();
                } catch (Exception e) {
                    LOGGER.warn("Question '{}' failed for entry {}", question, entryName, e);
                    answer = ChatMessage.errorMessage(e);
                }
                ChatMessage finalAnswer = answer;
                UiTaskExecutor.runInJavaFXThread(() -> chatHistory.addAll(userMessage, finalAnswer));

                task.updateProgress(++done, total);
            }
        }
    }

    private void ingestFiles(BibDatabaseContext context, BibEntry entry) {
        for (LinkedFile linkedFile : entry.getFiles()) {
            try {
                aiService.getIngestionTaskAggregator().startWithFuture(new GenerateEmbeddingsTaskRequest(
                        filePreferences,
                        aiService.getIngestedDocumentsRepository(),
                        aiService.getEmbeddingsStore(),
                        aiService.getCurrentEmbeddingModel(),
                        aiService.getCurrentDocumentSplitter(),
                        context,
                        linkedFile
                ), false).getKey().get();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                LOGGER.warn("Embedding generation failed for {}", linkedFile.getLink(), e);
            }
        }
    }
}
