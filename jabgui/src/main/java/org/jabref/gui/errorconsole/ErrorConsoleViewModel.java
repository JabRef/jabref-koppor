package org.jabref.gui.errorconsole;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javafx.beans.property.ListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.ObservableList;

import org.jabref.gui.AbstractViewModel;
import org.jabref.gui.ClipBoardManager;
import org.jabref.gui.DialogService;
import org.jabref.gui.desktop.os.NativeDesktop;
import org.jabref.gui.logging.LogMessages;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.util.BindingsHelper;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.os.OS;
import org.jabref.logic.util.BuildInfo;

import com.tobiasdiez.easybind.EasyBind;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorConsoleViewModel extends AbstractViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorConsoleViewModel.class);

    private final DialogService dialogService;
    private final GuiPreferences preferences;
    private final ClipBoardManager clipBoardManager;
    private final BuildInfo buildInfo;
    private final ListProperty<LogEventViewModel> allMessagesData;

    public ErrorConsoleViewModel(DialogService dialogService, GuiPreferences preferences, ClipBoardManager clipBoardManager, BuildInfo buildInfo) {
        this.dialogService = Objects.requireNonNull(dialogService);
        this.preferences = Objects.requireNonNull(preferences);
        this.clipBoardManager = Objects.requireNonNull(clipBoardManager);
        this.buildInfo = Objects.requireNonNull(buildInfo);
        ObservableList<LogEventViewModel> eventViewModels = EasyBind.map(BindingsHelper.forUI(LogMessages.getInstance().getMessages()), LogEventViewModel::new);
        allMessagesData = new ReadOnlyListWrapper<>(eventViewModels);
    }

    public ListProperty<LogEventViewModel> allMessagesDataProperty() {
        return this.allMessagesData;
    }

    /**
     * Concatenates the formatted message of the given {@link LogEventViewModel}s by using a new line separator.
     *
     * @return all messages as String
     */
    private String getLogMessagesAsString(List<LogEventViewModel> messages) {
        return messages.stream()
                       .map(LogEventViewModel::getDetailedText)
                       .collect(Collectors.joining(OS.NEWLINE));
    }

    /**
     * Copies the whole log to the clipboard
     */
    public void copyLog() {
        copyLog(allMessagesData);
    }

    /**
     * Copies the given list of {@link LogEventViewModel}s to the clipboard.
     */
    public void copyLog(List<LogEventViewModel> messages) {
        if (messages.isEmpty()) {
            return;
        }
        clipBoardManager.setContent(getLogMessagesAsString(messages));
        dialogService.notify(Localization.lang("Log copied to clipboard."));
    }

    /**
     * Copies the detailed text of the given {@link LogEventViewModel} to the clipboard.
     */
    public void copyLogEntry(LogEventViewModel logEvent) {
        clipBoardManager.setContent(logEvent.getDetailedText());
    }

    /**
     * Clears the current log
     */
    public void clearLog() {
        LogMessages.getInstance().clear();
    }

    /**
     * Opens a new issue on GitHub and copies log to clipboard.
     */
    public void reportIssue() {
        try {
            // System info
            String systemInfo = "JabRef %s%n%s %s %s %nJava %s".formatted(buildInfo.version, BuildInfo.OS,
                    BuildInfo.OS_VERSION, BuildInfo.OS_ARCH, BuildInfo.JAVA_VERSION);
            // Steps to reproduce
            String howToReproduce = "Steps to reproduce:\n\n1. ...\n2. ...\n3. ...";
            // Log messages
            String issueDetails = "<details>\n" + "<summary>" + "Detail information:" + "</summary>\n\n```\n"
                    + getLogMessagesAsString(allMessagesData) + "\n```\n\n</details>";
            clipBoardManager.setContent(issueDetails);
            // Bug report body
            String issueBody = systemInfo + "\n\n" + howToReproduce + "\n\n" + "Paste your log details here.";

            dialogService.notify(Localization.lang("Issue on GitHub successfully reported."));
            dialogService.showInformationDialogAndWait(Localization.lang("Issue report successful"),
                    Localization.lang("Your issue was reported in your browser.") + "\n" +
                            Localization.lang("The log and exception information was copied to your clipboard.") + " " +
                            Localization.lang("Please paste this information (with Ctrl+V) in the issue description.") + "\n" +
                            Localization.lang("Please also add all steps to reproduce this issue, if possible."));

            URIBuilder uriBuilder = new URIBuilder()
                    .setScheme("https").setHost("github.com")
                    .setPath("/JabRef/jabref/issues/new")
                    .setParameter("body", issueBody);
            NativeDesktop.openBrowser(uriBuilder.build().toString(), preferences.getExternalApplicationsPreferences());
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("Problem opening url", e);
        }
    }
}
