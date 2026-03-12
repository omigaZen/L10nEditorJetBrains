# L10n Editor for JetBrains

A JetBrains IDE plugin for editing L10n localization files with AI translation support.

## Features

- **TSV-based File Format**: Simple tab-separated format (`*.l10n.tsv`)
- **AI Translation**: Support for OpenAI, Claude, and Baidu translation APIs
- **Inlay Hints**: Shows `AI:` prefix for AI-translated cells
- **C# Code Generation**: Generate C# localization key classes
- **Editor Toolbar**: Quick access to common operations

## Installation

1. Download the latest plugin zip from [Releases](https://github.com/omigaZen/L10nEditorJetBrains/releases)
2. Open your JetBrains IDE (IntelliJ IDEA, Rider, etc.)
3. Go to `File` → `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
4. Select the downloaded zip file
5. Restart the IDE

## Usage

### Creating a New L10n File

1. Right-click in Project view → `New` → `L10n File`
2. Enter a file name (e.g., `game` → creates `game.l10n.tsv`)

### File Format

```
key	zh-CN	en-US	ja-JP
hero	英雄	hero	英雄（えいゆう）
attack	攻击	attack	攻撃
```

- First column: `key`
- Other columns: language codes

### AI Translation

1. Open a `.l10n.tsv` file
2. Click **AI Translate** in the toolbar
3. Empty cells will be automatically translated

### Adding Languages

1. Click **Add Language** in the toolbar
2. Select the language to add

### Generating C# Code

1. Click **Generate Code** in the toolbar
2. C# code will be copied to clipboard

## Configuration

Go to `Settings` → `Tools` → `L10n Editor` to configure:

- **AI Provider**: OpenAI, Claude, or Baidu
- **API Key**: Your API key
- **Custom Endpoint**: (Optional) Custom API endpoint
- **Model**: (Optional) Model name

## Meta File Format

AI-translated cells are tracked in a `.xxx.l10n_meta` file:

```
hero.en-US.hero
hero.ja-JP.英雄（えいゆう）
```

Format: `<key>.<language>.<translated_text>`

Only cells where all three values match are considered AI-translated.

## Building

```bash
./gradlew buildPlugin
```

The plugin zip will be in `build/distributions/L10nEditorJetBrains-0.0.0.zip`

## Requirements

- JetBrains IDE 2024.1 or later (IDEA, Rider, PyCharm, etc.)
- Java 17+

## License

MIT License

## Contributing

Pull requests are welcome!
