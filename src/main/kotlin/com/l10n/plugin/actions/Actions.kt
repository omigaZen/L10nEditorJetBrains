package com.l10n.plugin.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.l10n.plugin.editor.L10nAiHintManager
import com.l10n.plugin.format.L10nFileFormat
import com.l10n.plugin.generator.CSharpCodeGenerator
import com.l10n.plugin.model.LANGUAGE_NAMES
import com.l10n.plugin.model.Translation
import com.l10n.plugin.service.AiTranslateService
import com.l10n.plugin.settings.L10nSettings
import com.l10n.plugin.settings.L10nSettingsConfigurable
import java.io.File

/**
 * Add a new language column to the L10n file
 */
class AddLanguageAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = getL10nFile(e) ?: return
        execute(project, file)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = getL10nFile(e) != null
    }

    companion object {
        fun execute(project: Project, file: VirtualFile) {
            val availableLanguages = LANGUAGE_NAMES.entries.map { "${it.key} - ${it.value}" }.toTypedArray()
            val selected = Messages.showEditableChooseDialog(
                "Select language to add:",
                "Add Language",
                Messages.getQuestionIcon(),
                availableLanguages,
                null,
                null
            ) ?: return

            // Extract language code
            val langCode = selected.substringBefore(" - ").trim()

            val document = FileDocumentManager.getInstance().getDocument(file) ?: return

            ApplicationManager.getApplication().runWriteAction {
                val content = document.text
                val lines = content.lines().toMutableList()

                if (lines.isEmpty()) {
                    lines.add("key\t$langCode")
                } else {
                    // Add language to header
                    lines[0] = lines[0] + "\t" + langCode

                    // Add empty column to each data row
                    for (i in 1 until lines.size) {
                        if (lines[i].isNotBlank()) {
                            lines[i] = lines[i] + "\t"
                        }
                    }
                }

                document.setText(lines.joinToString("\n"))
            }
        }
    }
}

/**
 * Translate empty cells using AI
 */
class AiTranslateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = getL10nFile(e) ?: return
        execute(project, file)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = getL10nFile(e) != null
    }

    companion object {
        fun execute(project: Project, file: VirtualFile) {
            // Check if API is configured
            val settings = L10nSettings.getInstance()
            val apiKey = settings.state.apiKey

            if (apiKey.isNullOrBlank()) {
                showApiNotConfiguredNotification(project)
                return
            }

            val document = FileDocumentManager.getInstance().getDocument(file) ?: return
            val content = document.text

            // Read AI records from meta file
            val metaFile = L10nFileFormat.getMetaFile(File(file.path))
            val aiRecords = if (metaFile.exists()) {
                L10nFileFormat.parseMetaContent(metaFile.readText())
            } else {
                emptySet()
            }

            val l10nFile = L10nFileFormat.parseContent(content, aiRecords)

            // Find empty cells - track entry key for each row
            data class TranslationTask(val entryKey: String, val rowIndex: Int, val lang: String, val sourceText: String)
            val translationsToMake = mutableListOf<TranslationTask>()

            for ((rowIndex, entry) in l10nFile.entries.withIndex()) {
                for (lang in l10nFile.languages) {
                    val translation = entry.translations[lang]
                    if (translation == null || translation.text.isEmpty()) {
                        // Find source text from first non-empty translation
                        val sourceText = l10nFile.languages.firstNotNullOfOrNull { sourceLang ->
                            entry.translations[sourceLang]?.text?.takeIf { it.isNotEmpty() }
                        }

                        if (sourceText != null) {
                            translationsToMake.add(TranslationTask(entry.key, rowIndex, lang, sourceText))
                        }
                    }
                }
            }

            if (translationsToMake.isEmpty()) {
                showNotification(project, "No empty cells to translate", NotificationType.INFORMATION)
                return
            }

            // Perform translations in background task
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Translating...", true) {
                private val results = mutableMapOf<Triple<String, Int, String>, String>()
                private val errors = mutableListOf<String>()

                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Translating ${translationsToMake.size} cells..."

                    for ((index, task) in translationsToMake.withIndex()) {
                        if (indicator.isCanceled) break

                        val (entryKey, rowIndex, targetLang, sourceText) = task
                        indicator.text2 = "Translating: $sourceText"

                        val result = AiTranslateService.instance.translate(
                            sourceText,
                            l10nFile.languages[0],
                            targetLang
                        )

                        if (result.isSuccess) {
                            results[Triple(entryKey, rowIndex, targetLang)] = result.getOrThrow()
                        } else {
                            val exception = result.exceptionOrNull()
                            val errorMsg = exception?.message ?: exception?.javaClass?.simpleName ?: "Unknown error"
                            errors.add("Key '$entryKey' ($targetLang): $errorMsg")
                        }

                        indicator.fraction = (index + 1).toDouble() / translationsToMake.size
                    }
                }

                override fun onSuccess() {
                    if (results.isEmpty()) {
                        // All failed - show detailed error
                        val errorDetail = if (errors.size == 1) {
                            errors.first()
                        } else {
                            "${errors.size} errors:\n" + errors.take(5).joinToString("\n") +
                                    if (errors.size > 5) "\n... and ${errors.size - 5} more" else ""
                        }
                        showErrorNotification(project, "Translation failed", errorDetail)
                        return
                    }

                    // Update document on EDT
                    ApplicationManager.getApplication().runWriteAction {
                        val newAiRecords = aiRecords.toMutableSet()

                        for ((key, translatedText) in results) {
                            val (entryKey, rowIndex, lang) = key
                            val entry = l10nFile.entries[rowIndex]
                            entry.translations[lang] = Translation(translatedText, "ai")
                            // Add AI record in format: <key>.<language>.<text>
                            newAiRecords.add(L10nFileFormat.createAiRecord(entryKey, lang, translatedText))
                        }

                        val newContent = L10nFileFormat.generateContent(l10nFile)
                        document.setText(newContent)

                        // Update meta file
                        metaFile.writeText(L10nFileFormat.generateMetaContent(newAiRecords))
                    }

                    // Refresh AI hints in editor
                    refreshAiHints(project, file)

                    if (errors.isEmpty()) {
                        showNotification(project, "Translated ${results.size} cells successfully", NotificationType.INFORMATION)
                    } else {
                        val errorDetail = errors.take(3).joinToString("\n") +
                                if (errors.size > 3) "\n... and ${errors.size - 3} more" else ""
                        showWarningNotification(project, "Translated ${results.size} cells, ${errors.size} failed", errorDetail)
                    }
                }

                override fun onThrowable(error: Throwable) {
                    showErrorNotification(project, "Translation error", error.message ?: error.javaClass.simpleName)
                }
            })
        }

        private fun refreshAiHints(project: Project, file: VirtualFile) {
            ApplicationManager.getApplication().invokeLater {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val fileEditors = fileEditorManager.getEditors(file)

                for (editor in fileEditors) {
                    if (editor is TextEditor) {
                        L10nAiHintManager.install(editor.editor, file)
                    }
                }
            }
        }

        private fun showApiNotConfiguredNotification(project: Project) {
            val notification = Notification(
                "L10nEditor",
                "L10n Editor",
                "AI API is not configured. Please configure your API key in settings.",
                NotificationType.WARNING
            )

            notification.addAction(NotificationAction.create("Open Settings") { _, _ ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, L10nSettingsConfigurable::class.java)
            })

            Notifications.Bus.notify(notification, project)
        }
    }
}

/**
 * Generate C# code from L10n file
 */
class GenerateCodeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = getL10nFile(e) ?: return
        execute(project, file)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = getL10nFile(e) != null
    }

    companion object {
        fun execute(project: Project, file: VirtualFile) {
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return
            val content = document.text

            // Read AI records from meta file
            val metaFile = L10nFileFormat.getMetaFile(File(file.path))
            val aiRecords = if (metaFile.exists()) {
                L10nFileFormat.parseMetaContent(metaFile.readText())
            } else {
                emptySet()
            }

            val l10nFile = L10nFileFormat.parseContent(content, aiRecords)

            // Generate class name from file name
            val baseName = file.name.removeSuffix(".l10n.tsv")
            val className = baseName.split("_", "-", " ").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } } + "Keys"

            val code = CSharpCodeGenerator.generate(l10nFile, className)

            // Copy to clipboard
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val selection = java.awt.datatransfer.StringSelection(code)
            clipboard.setContents(selection, null)

            showNotification(project, "C# code copied to clipboard ($className)", NotificationType.INFORMATION)
        }
    }
}

/**
 * Create new L10n file
 */
class NewL10nFileAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val name = Messages.showInputDialog(
            project,
            "Enter file name (without extension):",
            "New L10n File",
            Messages.getQuestionIcon()
        ) ?: return

        if (name.isBlank()) return

        // Get parent directory from context
        val parent = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE)
            ?: project.baseDir

        val fileName = if (name.endsWith(".l10n.tsv")) name else "$name.l10n.tsv"
        val newFile = File(parent.path, fileName)

        if (newFile.exists()) {
            showNotification(project, "File already exists: $fileName", NotificationType.ERROR)
            return
        }

        ApplicationManager.getApplication().runWriteAction {
            newFile.writeText("key\tzh-CN\ten-US\n")
        }

        // Refresh and open file
        parent.refresh(false, false)
        val vFile = parent.findChild(fileName)
        if (vFile != null) {
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vFile, true)
        }

        showNotification(project, "Created $fileName", NotificationType.INFORMATION)
    }
}

// Helper functions
private fun getL10nFile(e: AnActionEvent): VirtualFile? {
    val file = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE) ?: return null
    return if (file.name.endsWith(".l10n.tsv")) file else null
}

private fun showNotification(project: Project, message: String, type: NotificationType) {
    val notification = Notification(
        "L10nEditor",
        "L10n Editor",
        message,
        type
    )
    Notifications.Bus.notify(notification, project)
}

private fun showWarningNotification(project: Project, title: String, detail: String) {
    val notification = Notification(
        "L10nEditor",
        "L10n Editor",
        "$title\n\n$detail",
        NotificationType.WARNING
    )
    Notifications.Bus.notify(notification, project)
}

private fun showErrorNotification(project: Project, title: String, detail: String) {
    val notification = Notification(
        "L10nEditor",
        "L10n Editor",
        "$title\n\n$detail",
        NotificationType.ERROR
    )
    Notifications.Bus.notify(notification, project)
}
