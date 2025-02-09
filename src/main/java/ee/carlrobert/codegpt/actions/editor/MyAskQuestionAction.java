package ee.carlrobert.codegpt.actions.editor;

import com.intellij.openapi.application.ActionsKt;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import ee.carlrobert.codegpt.Icons;
import ee.carlrobert.codegpt.conversations.message.Message;
import ee.carlrobert.codegpt.toolwindow.chat.ChatToolWindowContentManager;
import ee.carlrobert.codegpt.ui.UIUtil;
import ee.carlrobert.codegpt.util.file.FileUtil;
import kotlin.Unit;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

import static java.lang.String.format;

public class MyAskQuestionAction extends BaseEditorAction {

    private static String previousUserPrompt = "";

    MyAskQuestionAction() {
        super(Icons.QuestionMark);
    }

    @Override
    protected void actionPerformed(Project project, Editor editor, String selectedText) {
        var homeDirectory = System.getProperty("user.home");
        var file = new File(homeDirectory + "/code-gpt-output.md");
        var documentManager = FileDocumentManager.getInstance();
        var virtualFile = VfsUtil.findFileByIoFile(file, true);
        ActionsKt.runReadAction(() -> {
            var document = documentManager.getDocument(virtualFile);
            assert document != null;
            var text = document.getText();
            var lastQueryEnd = text.lastIndexOf("## End AI");
            if(lastQueryEnd == -1) {
                lastQueryEnd = 0;
            }
            var query = text.substring(lastQueryEnd, document.getTextLength());
            if (query != null && !query.isEmpty()) {
                var fileExtension = FileUtil.getFileExtension(editor.getVirtualFile().getName());
                var dialog = new CustomPromptDialog(previousUserPrompt);
                if (dialog.showAndGet()) {
                    previousUserPrompt = dialog.getUserPrompt();
                    var message = new Message(
                            format("%s%n```%s%n%s%n```", previousUserPrompt, fileExtension, query));
                    SwingUtilities.invokeLater(() ->
                            project.getService(ChatToolWindowContentManager.class).sendMessage(message));
                }
            }
            return Unit.INSTANCE;
        });
    }

    public static class CustomPromptDialog extends DialogWrapper {

        private final JTextArea userPromptTextArea;

        public CustomPromptDialog(String previousUserPrompt) {
            super(true);
            this.userPromptTextArea = new JTextArea(previousUserPrompt);
            this.userPromptTextArea.setCaretPosition(previousUserPrompt.length());
            setTitle("Custom Prompt");
            setSize(400, getRootPane().getPreferredSize().height);
            init();
        }

        @Nullable
        public JComponent getPreferredFocusedComponent() {
            return userPromptTextArea;
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            userPromptTextArea.setLineWrap(true);
            userPromptTextArea.setWrapStyleWord(true);
            userPromptTextArea.setMargin(JBUI.insets(5));
            UIUtil.addShiftEnterInputMap(userPromptTextArea, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    clickDefaultButton();
                }
            });

            return FormBuilder.createFormBuilder()
                    .addComponent(UI.PanelFactory.panel(userPromptTextArea)
                            .withLabel("Prefix:")
                            .moveLabelOnTop()
                            .withComment("Example: Find bugs in the following code")
                            .createPanel())
                    .getPanel();
        }

        public String getUserPrompt() {
            return userPromptTextArea.getText();
        }
    }
}
