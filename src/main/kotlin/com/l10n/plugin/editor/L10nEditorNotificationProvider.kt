package com.l10n.plugin.editor

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.l10n.plugin.actions.*

/**
 * Editor Notification Provider that shows a toolbar above L10n files.
 */
class L10nEditorNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {

    companion object {
        private val KEY = Key.create<EditorNotificationPanel>("l10n.editor.panel")
        private val LISTENER_KEY = Key.create<DocumentListener>("l10n.editor.listener")
    }

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(
        file: VirtualFile,
        editor: FileEditor,
        project: Project
    ): EditorNotificationPanel? {
        // Check if this is an L10n file
        if (!isL10nFile(file)) return null

        val panel = EditorNotificationPanel()
        panel.setText("L10n Editor")

        // Add action buttons (removed Add Row)
        panel.createActionLabel("Add Language") {
            AddLanguageAction.execute(project, file)
        }

        panel.createActionLabel("AI Translate") {
            AiTranslateAction.execute(project, file)
        }

        panel.createActionLabel("Generate Code") {
            GenerateCodeAction.execute(project, file)
        }

        // Add AI hints if we have a text editor
        if (editor is TextEditor) {
            val textEditor = editor.editor

            // Install AI hints
            L10nAiHintManager.install(textEditor, file)

            // Add document listener to refresh hints on document change
            val existingListener = textEditor.getUserData(LISTENER_KEY)
            if (existingListener == null) {
                val listener = object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        // Refresh AI hints after document changes
                        L10nAiHintManager.install(textEditor, file)
                    }
                }
                textEditor.document.addDocumentListener(listener)
                textEditor.putUserData(LISTENER_KEY, listener)
            }
        }

        return panel
    }

    private fun isL10nFile(file: VirtualFile): Boolean {
        return file.name.endsWith(".l10n.tsv")
    }
}
