package ee.carlrobert.codegpt.actions.toolwindow;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.LightVirtualFile;
import ee.carlrobert.codegpt.actions.ActionType;
import ee.carlrobert.codegpt.actions.editor.EditorActionsUtil;
import ee.carlrobert.codegpt.conversations.ConversationsState;
import ee.carlrobert.codegpt.telemetry.TelemetryAction;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

public class OpenInEditorAction extends AnAction {

    public OpenInEditorAction() {
        super("Open In Editor", "Open conversation in editor", AllIcons.Actions.SplitVertically);
        EditorActionsUtil.registerAction(this);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        super.update(event);
        var currentConversation = ConversationsState.getCurrentConversation();
        var isEnabled = currentConversation != null && !currentConversation.getMessages().isEmpty();
        event.getPresentation().setEnabled(isEnabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        try {
            var project = e.getProject();
            var currentConversation = ConversationsState.getCurrentConversation();
            if (project != null && currentConversation != null) {
                var homeDirectory = System.getProperty("user.home");
                var file = new File(homeDirectory + "/code-gpt-output.md");
                var fileContent = currentConversation
                        .getMessages()
                        .stream()
                        .map(it -> format("### User:%n%s%n### CodeGPT:%n%s%n", it.getPrompt(),
                                it.getResponse()))
                        .collect(Collectors.joining());
                // TODOPB dodaj  ze jak jest otwierajace ``` a nie ma zamykajacego ostatniego, to dodaj recznie i tyle
                var documentManager = FileDocumentManager.getInstance();
                var virtualFile = VfsUtil.findFileByIoFile(file, true);
                var document = documentManager.getDocument(virtualFile);

                if (document != null) {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        document.setText(fileContent); // Replace the content of the document
                    });
                    FileEditorManager.getInstance(project).openFile(virtualFile, true);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
