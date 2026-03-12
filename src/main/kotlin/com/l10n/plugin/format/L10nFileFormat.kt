package com.l10n.plugin.format

import com.l10n.plugin.model.Entry
import com.l10n.plugin.model.L10nFile
import com.l10n.plugin.model.Translation
import java.io.File

/**
 * L10n file format handler for TSV + metadata storage.
 *
 * File structure:
 * - xxx.l10n.tsv: Translation data (key + translations, tab-separated)
 *   Format: key    zh-CN    en-US    zh-TW    ja-JP
 *           hero    英雄    hero    英雄    英雄（えいゆう）
 * - .xxx.l10n_meta: Metadata (AI-translated cells only)
 *   Format: each line is "<key>.<language>.<translated_text>" for AI cells
 *   Example: hero.en-US.hero
 *            hero.ja-JP.英雄（えいゆう）
 */
object L10nFileFormat {

    /**
     * Parse TSV content to L10nFile
     * @param content TSV file content
     * @param aiRecords Set of AI records in format "<key>.<language>.<text>"
     */
    fun parseContent(content: String, aiRecords: Set<String> = emptySet()): L10nFile {
        val lines = content.lines()
        if (lines.isEmpty()) {
            return L10nFile()
        }

        // Parse header
        val headerFields = lines[0].split("\t")
        val languages = headerFields.drop(1).toMutableList()

        // Parse data rows
        val entries = mutableListOf<Entry>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue

            val fields = line.split("\t")
            if (fields.isEmpty()) continue

            val key = fields[0]
            val translations = mutableMapOf<String, Translation>()

            for ((langIndex, lang) in languages.withIndex()) {
                val text = fields.getOrNull(langIndex + 1) ?: ""

                // Check if this cell matches an AI record: <key>.<language>.<text>
                val aiRecordKey = "$key.$lang.$text"
                val source = when {
                    text.isEmpty() -> null
                    aiRecords.contains(aiRecordKey) -> "ai"
                    else -> "user"
                }

                translations[lang] = Translation(text, source)
            }

            entries.add(Entry(key, translations))
        }

        return L10nFile(languages = languages, entries = entries)
    }

    /**
     * Generate TSV content from L10nFile
     */
    fun generateContent(l10nFile: L10nFile): String {
        val sb = StringBuilder()

        // Header row
        sb.append("key")
        for (lang in l10nFile.languages) {
            sb.append("\t")
            sb.append(lang)
        }
        sb.append("\n")

        // Data rows
        for (entry in l10nFile.entries) {
            sb.append(entry.key)
            for (lang in l10nFile.languages) {
                sb.append("\t")
                sb.append(entry.translations[lang]?.text ?: "")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    /**
     * Parse meta file content to AI records set
     * Format: each line is "<key>.<language>.<text>"
     */
    fun parseMetaContent(content: String): Set<String> {
        return content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Generate meta file content from AI records set
     */
    fun generateMetaContent(aiRecords: Set<String>): String {
        return aiRecords.joinToString("\n")
    }

    /**
     * Create AI record key: "<key>.<language>.<text>"
     */
    fun createAiRecord(key: String, language: String, text: String): String {
        return "$key.$language.$text"
    }

    /**
     * Get meta file path for a given TSV file
     */
    fun getMetaFile(tsvFile: File): File {
        val baseName = tsvFile.name.removeSuffix(".l10n.tsv")
        return File(tsvFile.parentFile, ".$baseName.l10n_meta")
    }

    /**
     * Validate TSV format
     */
    fun validate(content: String): String? {
        val lines = content.lines()
        if (lines.isEmpty()) {
            return "Empty file"
        }

        val headerFields = lines[0].split("\t")
        if (headerFields.isEmpty()) {
            return "Invalid header: empty first row"
        }

        if (headerFields[0] != "key") {
            return "Invalid header: first column must be 'key'"
        }

        return null
    }

    /**
     * Read L10nFile from files
     */
    fun read(tsvFile: File): Pair<L10nFile, String?> {
        if (!tsvFile.exists()) {
            return Pair(L10nFile(), null)
        }

        val content = tsvFile.readText(Charsets.UTF_8)
        val validationError = validate(content)
        if (validationError != null) {
            return Pair(L10nFile(), validationError)
        }

        val metaFile = getMetaFile(tsvFile)
        val aiRecords = if (metaFile.exists()) {
            parseMetaContent(metaFile.readText(Charsets.UTF_8))
        } else {
            emptySet()
        }

        return Pair(parseContent(content, aiRecords), null)
    }

    /**
     * Write L10nFile to files
     */
    fun write(tsvFile: File, l10nFile: L10nFile) {
        // Build AI records set: <key>.<language>.<text>
        val aiRecords = mutableSetOf<String>()
        for (entry in l10nFile.entries) {
            entry.translations.forEach { (lang, translation) ->
                if (translation.source == "ai" && translation.text.isNotEmpty()) {
                    aiRecords.add(createAiRecord(entry.key, lang, translation.text))
                }
            }
        }

        // Write TSV
        tsvFile.writeText(generateContent(l10nFile), Charsets.UTF_8)

        // Write meta
        val metaFile = getMetaFile(tsvFile)
        if (aiRecords.isNotEmpty()) {
            metaFile.writeText(generateMetaContent(aiRecords), Charsets.UTF_8)
        } else if (metaFile.exists()) {
            metaFile.delete()
        }
    }
}
