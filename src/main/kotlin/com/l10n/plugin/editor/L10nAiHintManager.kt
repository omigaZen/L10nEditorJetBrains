package com.l10n.plugin.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.l10n.plugin.format.L10nFileFormat
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.io.File

/**
 * Manages AI hints in the editor using inlay elements.
 * Shows "AI:" text prefix before AI-translated cells with tooltip "由AI翻译".
 */
object L10nAiHintManager {
    private val INLAYS_KEY = Key.create<MutableList<Inlay<*>>>("l10n.ai.inlays")

    fun install(editor: Editor, file: VirtualFile) {
        // Ensure we're on EDT
        if (!ApplicationManager.getApplication().isDispatchThread) {
            ApplicationManager.getApplication().invokeLater {
                install(editor, file)
            }
            return
        }

        // Remove existing hints
        removeHints(editor)

        val metaFile = L10nFileFormat.getMetaFile(File(file.path))
        if (!metaFile.exists()) return

        val aiRecords = L10nFileFormat.parseMetaContent(metaFile.readText())
        if (aiRecords.isEmpty()) return

        val document = editor.document
        val content = document.text
        val l10nFile = L10nFileFormat.parseContent(content, aiRecords)

        val inlays = mutableListOf<Inlay<*>>()

        for ((rowIndex, entry) in l10nFile.entries.withIndex()) {
            for ((langIndex, lang) in l10nFile.languages.withIndex()) {
                val translation = entry.translations[lang] ?: continue
                if (translation.source == "ai" && translation.text.isNotEmpty()) {
                    // Find the position in the document
                    val lineIndex = rowIndex + 1 // +1 for header
                    if (lineIndex >= document.lineCount) continue

                    val lineStartOffset = document.getLineStartOffset(lineIndex)
                    val lineEndOffset = document.getLineEndOffset(lineIndex)
                    val lineText = document.getText(
                        com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset)
                    )

                    // Find the column position for this language
                    val columns = lineText.split("\t")
                    if (columns.size <= langIndex + 1) continue

                    // Calculate the start offset of the cell content
                    var cellStartOffset = lineStartOffset
                    for (i in 0..langIndex) {
                        if (i < columns.size) {
                            cellStartOffset += columns[i].length + 1 // +1 for tab
                        }
                    }

                    // Skip leading whitespace in the cell
                    val cellContent = columns.getOrNull(langIndex + 1) ?: ""
                    val leadingWhitespace = cellContent.takeWhile { it == ' ' || it == '\t' }
                    cellStartOffset += leadingWhitespace.length

                    // Add inlay with "AI:" prefix
                    try {
                        val inlay = editor.inlayModel.addInlineElement(
                            cellStartOffset,
                            false,  // relatesToPrecedingText = false, associate with following text
                            AiPrefixRenderer()
                        )
                        if (inlay != null) {
                            inlays.add(inlay)
                        }
                    } catch (e: Exception) {
                        // Ignore errors during inlay creation
                    }
                }
            }
        }

        editor.putUserData(INLAYS_KEY, inlays)
    }

    fun removeHints(editor: Editor) {
        val inlays = editor.getUserData(INLAYS_KEY) ?: return

        for (inlay in inlays) {
            try {
                if (inlay.isValid) {
                    inlay.dispose()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        inlays.clear()
        editor.putUserData(INLAYS_KEY, null)
    }

    /**
     * Refresh AI hints after document change
     */
    fun refresh(editor: Editor, file: VirtualFile) {
        install(editor, file)
    }

    /**
     * Custom renderer for "AI:" prefix inlay
     */
    private class AiPrefixRenderer : EditorCustomElementRenderer {

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val font = inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN)
                .deriveFont(Font.ITALIC)
            val metrics = inlay.editor.contentComponent.getFontMetrics(font)
            return metrics.stringWidth("AI:") + 4 // 4px padding after text
        }

        override fun paint(
            inlay: Inlay<*>,
            g: Graphics2D,
            targetRegion: Rectangle2D,
            textAttributes: TextAttributes
        ) {
            val editor = inlay.editor

            // Use grey italic font like parameter hints
            val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
                .deriveFont(Font.ITALIC)
            g.font = font

            // Grey color for AI hint
            g.color = JBColor(Color(128, 128, 128), Gray._128)

            // Draw "AI:" text
            val x = targetRegion.x.toFloat()
            val y = (targetRegion.y + editor.ascent).toFloat()
            g.drawString("AI:", x, y)
        }
    }
}
