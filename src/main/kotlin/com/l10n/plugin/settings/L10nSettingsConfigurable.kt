package com.l10n.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.*

class L10nSettingsConfigurable : Configurable {
    private var modified = false

    private val providerCombo = ComboBox(arrayOf("openai", "claude", "baidu"))
    private val apiKeyField = JBTextField()
    private val secretKeyField = JBPasswordField()
    private val endpointField = JBTextField()
    private val modelField = JBTextField()

    override fun getDisplayName(): String = "L10n Editor"

    override fun createComponent(): JComponent {
        val settings = L10nSettings.getInstance().state

        // Initialize values
        providerCombo.selectedItem = settings.aiProvider
        apiKeyField.text = settings.apiKey
        secretKeyField.text = settings.secretKey
        endpointField.text = settings.endpoint
        modelField.text = settings.model

        // Set preferred sizes
        apiKeyField.preferredSize = Dimension(400, apiKeyField.preferredSize.height)
        endpointField.preferredSize = Dimension(400, endpointField.preferredSize.height)
        modelField.preferredSize = Dimension(400, modelField.preferredSize.height)

        // Add listeners for modification tracking
        providerCombo.addActionListener { modified = true }
        apiKeyField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { modified = true }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { modified = true }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { modified = true }
        })

        // Update UI based on provider selection
        updateProviderUI()
        providerCombo.addActionListener { updateProviderUI() }

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("AI Provider:", providerCombo)
            .addLabeledComponent("API Key:", apiKeyField)
            .addLabeledComponent("Secret Key (Baidu):", secretKeyField)
            .addLabeledComponent("Custom Endpoint:", endpointField)
            .addLabeledComponent("Model:", modelField)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    private fun updateProviderUI() {
        val provider = providerCombo.selectedItem as String
        when (provider) {
            "baidu" -> {
                secretKeyField.isVisible = true
                endpointField.text = "https://fanyi-api.baidu.com/api/trans/vip/translate"
            }
            "openai" -> {
                secretKeyField.isVisible = false
                if (endpointField.text.isBlank()) {
                    endpointField.text = "https://api.openai.com/v1"
                }
            }
            "claude" -> {
                secretKeyField.isVisible = false
                if (endpointField.text.isBlank()) {
                    endpointField.text = "https://api.anthropic.com"
                }
            }
        }
    }

    override fun isModified(): Boolean = modified

    override fun apply() {
        val settings = L10nSettings.getInstance().state
        settings.aiProvider = providerCombo.selectedItem as String
        settings.apiKey = apiKeyField.text
        settings.secretKey = String(secretKeyField.password)
        settings.endpoint = endpointField.text
        settings.model = modelField.text
        modified = false
    }

    override fun reset() {
        val settings = L10nSettings.getInstance().state
        providerCombo.selectedItem = settings.aiProvider
        apiKeyField.text = settings.apiKey
        secretKeyField.text = settings.secretKey
        endpointField.text = settings.endpoint
        modelField.text = settings.model
        modified = false
    }
}
