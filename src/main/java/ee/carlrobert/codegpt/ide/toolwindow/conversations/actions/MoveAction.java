package ee.carlrobert.codegpt.ide.toolwindow.conversations.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import ee.carlrobert.codegpt.ide.conversations.Conversation;
import ee.carlrobert.codegpt.ide.conversations.ConversationsState;
import java.util.Optional;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public abstract class MoveAction extends AnAction {

  private final Runnable onRefresh;

  protected abstract Optional<Conversation> getConversation();

  protected MoveAction(String text, String description, Icon icon, Runnable onRefresh) {
    super(text, description, icon);
    this.onRefresh = onRefresh;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    getConversation()
        .ifPresent(conversation -> {
          ConversationsState.getInstance().setCurrentConversation(conversation);
          onRefresh.run();
        });
  }
}
