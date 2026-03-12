# L10n Editor Plugin

IntelliJ IDEA plugin for editing L10n localization files with AI translation support.

## Project Structure

```
src/main/kotlin/com/l10n/plugin/
├── L10nFileType.kt              # File type definition
├── actions/
│   └── Actions.kt               # AddRow, AddLanguage, AiTranslate, GenerateCode actions
├── editor/
│   └── L10nEditorNotificationProvider.kt  # Editor toolbar (shows above editor)
├── format/
│   └── L10nFileFormat.kt        # TSV + metadata parsing/generation
├── generator/
│   └── CSharpCodeGenerator.kt   # C# code generator
├── hints/
│   └── L10nInlayHintsProvider.kt # Inlay hints for AI-translated cells
├── model/
│   └── L10nModels.kt            # Data models
├── service/
│   └── AiTranslateService.kt    # AI translation (OpenAI, Claude, Baidu)
└── settings/
    ├── L10nSettings.kt          # Persistent settings
    └── L10nSettingsConfigurable.kt # Settings UI
```

## Key Features

1. **Text Editor Based**: Uses standard text editor instead of custom table editor
2. **Editor Toolbar**: Shows action buttons (Add Row, Add Language, AI Translate, Generate Code) above the editor
3. **Inlay Hints**: Shows grey "[AI]" hints for AI-translated cells
4. **File Format**:
   - `xxx.l10n.tsv` - TSV format translation data
   - `.xxx.l10n_meta` - Metadata for AI-translated cells

## Build

```bash
./gradlew buildPlugin
```

## Development Notes

- Uses `EditorNotificationProvider` to show toolbar above editor
- Uses `InlayHintsProvider` to show AI hints in editor
- TSV format with `key` as first column, languages as subsequent columns
- Metadata file tracks AI-translated cells using "rowIndex.languageCode" format
