package ee.carlrobert.codegpt.actions.editor

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.JBColor
import ee.carlrobert.codegpt.predictions.PredictionService
import ee.carlrobert.codegpt.ui.ObservableProperties
import ee.carlrobert.codegpt.ui.OverlayUtil
import ee.carlrobert.llm.client.openai.completion.ErrorDetails
import ee.carlrobert.llm.completion.CompletionEventListener
import okhttp3.sse.EventSource

class DiffCompletionListener(
    private val editor: Editor,
    private val observableProperties: ObservableProperties,
    private val selectionTextRange: TextRange
) : CompletionEventListener<String> {

    val predictionService = PredictionService()
    private var replacedLength = 0
    private var currentHighlighter: RangeHighlighter? = null

    override fun onMessage(message: String, eventSource: EventSource) {
    }

    override fun onComplete(messageBuilder: StringBuilder) {
        val message = messageBuilder.toString()
        predictionService.displayInlineDiffWithString(
            editor,
            getReplacedText(editor, selectionTextRange.startOffset, selectionTextRange.endOffset, message)
        )
    }

    fun getReplacedText(editor: Editor, startOffset: Int, endOffset: Int, newText: String): String  {
        val documentText = editor.document.text
        val prefix = documentText.substring(0, startOffset)
        val suffix = documentText.substring(endOffset)
        return prefix + newText + suffix
    }

    override fun onError(error: ErrorDetails, ex: Throwable) {
        observableProperties.loading.set(false)

        OverlayUtil.showNotification(
            error.message,
            NotificationType.ERROR,
            NotificationAction.createSimpleExpiring("Failed the diff") {
                BrowserUtil.open("https://codegpt.ee/#pricing")
            },
        )
    }
}
