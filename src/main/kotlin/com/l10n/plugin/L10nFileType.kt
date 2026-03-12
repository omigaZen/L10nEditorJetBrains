package com.l10n.plugin

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.lang.Language
import javax.swing.Icon

object L10nLanguage : Language("L10n") {
    override fun getDisplayName(): String = "L10n"
    override fun isCaseSensitive(): Boolean = true
}

object L10nFileType : LanguageFileType(L10nLanguage) {
    override fun getName(): String = "L10nFile"
    override fun getDescription(): String = "L10n localization file"
    override fun getDefaultExtension(): String = "l10n.tsv"
    override fun getIcon(): Icon = com.intellij.icons.AllIcons.FileTypes.Text // Use default text icon

    @JvmStatic
    val INSTANCE get() = this
}
