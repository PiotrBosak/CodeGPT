package ee.carlrobert.codegpt.toolwindow.chat;

import static com.intellij.openapi.ui.Messages.OK;
import static java.util.Objects.requireNonNull;

import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtil;
import ee.carlrobert.codegpt.EncodingManager;
import ee.carlrobert.codegpt.codecompletions.CompletionProgressNotifier;
import ee.carlrobert.codegpt.completions.ChatCompletionParameters;
import ee.carlrobert.codegpt.completions.CompletionResponseEventListener;
import ee.carlrobert.codegpt.conversations.Conversation;
import ee.carlrobert.codegpt.conversations.ConversationService;
import ee.carlrobert.codegpt.conversations.message.Message;
import ee.carlrobert.codegpt.events.CodeGPTEvent;
import ee.carlrobert.codegpt.telemetry.TelemetryAction;
import ee.carlrobert.codegpt.toolwindow.chat.ui.ChatMessageResponseBody;
import ee.carlrobert.codegpt.toolwindow.chat.ui.textarea.TotalTokensPanel;
import ee.carlrobert.codegpt.toolwindow.ui.ResponseMessagePanel;
import ee.carlrobert.codegpt.toolwindow.ui.UserMessagePanel;
import ee.carlrobert.codegpt.ui.OverlayUtil;
import ee.carlrobert.codegpt.ui.textarea.UserInputPanel;
import ee.carlrobert.llm.client.openai.completion.ErrorDetails;
import kotlin.Unit;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

abstract class ToolWindowCompletionResponseEventListener implements
        CompletionResponseEventListener {

    private static final Logger LOG = Logger.getInstance(
            ToolWindowCompletionResponseEventListener.class);
    private static final int UPDATE_INTERVAL_MS = 8;

    private final Project project;private final StringBuilder messageBuilder = new StringBuilder();
    private final EncodingManager encodingManager;

    private final ResponseMessagePanel responsePanel;
    private final UserMessagePanel userMessagePanel;
    private final ChatMessageResponseBody responseContainer;
    private final TotalTokensPanel totalTokensPanel;
    private final UserInputPanel textArea;

    private final Timer updateTimer = new Timer(UPDATE_INTERVAL_MS, e -> processBufferedMessages());
    private final ConcurrentLinkedQueue<String> messageBuffer = new ConcurrentLinkedQueue<>();
    private boolean stopped = false;
    private boolean streamResponseReceived = false;

    public ToolWindowCompletionResponseEventListener(
            Project project,
            UserMessagePanel userMessagePanel,
            ResponseMessagePanel responsePanel,
            TotalTokensPanel totalTokensPanel,
            UserInputPanel textArea) {
        this.encodingManager = EncodingManager.getInstance();
        this.project = project;
        this.userMessagePanel = userMessagePanel;
        this.responsePanel = responsePanel;
        this.responseContainer = (ChatMessageResponseBody) responsePanel.getContent();
        this.totalTokensPanel = totalTokensPanel;
        this.textArea = textArea;
    }

    public abstract void handleTokensExceededPolicyAccepted();

    @Override
    public void handleRequestOpen() {
        try {
            // Code that modifies UI or interacts with Swing components
            var homeDirectory = System.getProperty("user.home");
            var file = new File(homeDirectory + "/code-gpt-output.md");
            var documentManager = FileDocumentManager.getInstance();
            var virtualFile = VfsUtil.findFileByIoFile(file, true);
            ActionsKt.invokeLater(null, () -> {
                ActionsKt.runUndoTransparentWriteAction(() -> {
                    assert virtualFile != null;
                    var document = documentManager.getDocument(virtualFile);
                    assert document != null;
                    document.insertString(document.getTextLength(), "\n## AI\n");
                    virtualFile.refresh(false, false);
                    return Unit.INSTANCE;
                });
                return Unit.INSTANCE;
            });
        } catch (Exception e) {
            System.out.println("AAA");
            System.out.println(e.getMessage());
        }
        updateTimer.start();
    }

    @Override
    public void handleMessage(String partialMessage) {
        streamResponseReceived = true;

        try {
            messageBuilder.append(partialMessage);
            var ongoingTokens = encodingManager.countTokens(messageBuilder.toString());
            messageBuffer.offer(partialMessage);
            ApplicationManager.getApplication().invokeLater(() ->
                    totalTokensPanel.update(totalTokensPanel.getTokenDetails().getTotal() + ongoingTokens)
            );
        } catch (Exception e) {
            responseContainer.displayError("Something went wrong.");
            throw new RuntimeException("Error while updating the content", e);
        }
    }

    @Override
    public void handleError(ErrorDetails error, Throwable ex) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                if ("insufficient_quota".equals(error.getCode())) {
                    responseContainer.displayQuotaExceeded();
                } else {
                    responseContainer.displayError(error.getMessage());
                }
            } finally {

                stopStreaming(responseContainer);
            }
        });
    }

    @Override
    public void handleTokensExceeded(Conversation conversation, Message message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            var answer = OverlayUtil.showTokenLimitExceededDialog();
            if (answer == OK) {
                TelemetryAction.IDE_ACTION.createActionMessage()
                        .property("action", "DISCARD_TOKEN_LIMIT")
                        .property("model", conversation.getModel())
                        .send();

                ConversationService.getInstance().discardTokenLimits(conversation);
                handleTokensExceededPolicyAccepted();
            } else {
                stopStreaming(responseContainer);
            }
        });
    }

    @Override
    public void handleCompleted(String fullMessage, ChatCompletionParameters callParameters) {
        ConversationService.getInstance().saveMessage(fullMessage, callParameters);
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                responsePanel.enableAllActions(true);
                if (!streamResponseReceived && !fullMessage.isEmpty()) {
                    responseContainer.withResponse(fullMessage);
                }
                totalTokensPanel.updateUserPromptTokens(textArea.getText());
                totalTokensPanel.updateConversationTokens(callParameters.getConversation());
            } finally {
                stopStreaming(responseContainer);
            }


        });
    }

    @Override
    public void handleCodeGPTEvent(CodeGPTEvent event) {
        responseContainer.handleCodeGPTEvent(event);
    }

    private void processBufferedMessages() {
        if (messageBuffer.isEmpty()) {
            if (stopped) {
                updateTimer.stop();
                try {
                    var homeDirectory = System.getProperty("user.home");
                    var file = new File(homeDirectory + "/code-gpt-output.md");
                    var documentManager = FileDocumentManager.getInstance();
                    var virtualFile = VfsUtil.findFileByIoFile(file, true);
                    ActionsKt.invokeLater(null, () -> {
                        ActionsKt.runUndoTransparentWriteAction(() -> {
                            assert virtualFile != null;
                            var document = documentManager.getDocument(virtualFile);
                            assert document != null;
                            document.insertString(document.getTextLength(), "\n## End AI\n");
                            virtualFile.refresh(false, false);
                            return Unit.INSTANCE;
                        });
                        return Unit.INSTANCE;
                    });
                } catch (Exception e) {
                    System.out.println("BBB");
                    System.out.println(e.getMessage());
                }
            }
            return;
        }

        StringBuilder accumulatedMessage = new StringBuilder();
        String message;
        while ((message = messageBuffer.poll()) != null) {
            accumulatedMessage.append(message);
        }

        responseContainer.updateMessage(accumulatedMessage.toString());
    }

    private void stopStreaming(ChatMessageResponseBody responseContainer) {
        stopped = true;
        textArea.setSubmitEnabled(true);
        userMessagePanel.enableAllActions(true);
        responsePanel.enableAllActions(true);
        responseContainer.hideCaret();
    CompletionProgressNotifier.update(project, false);
  }
}
