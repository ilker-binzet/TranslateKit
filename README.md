<div align="center">

<img src="icon.png" width="120" alt="TranslateKit Logo">

# TranslateKit

**Multi-provider AI translation plugin for [MT Manager](https://mt2.cn)**

[![Version](https://img.shields.io/badge/version-0.6.1-blue?style=flat-square)](https://github.com/ilker-binzet/TranslateKit/releases)
[![SDK](https://img.shields.io/badge/MT%20Plugin%20SDK-v3%20stable-purple?style=flat-square)](https://gitee.com/L-JINBIN/mt-plugin-v3-demo)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square)](#)

*Translate Android string resources using Gemini, OpenAI, Claude, OpenRouter — or any OpenAI-compatible endpoint, including your own local server.*

</div>

---

## Overview

TranslateKit brings the power of modern AI translation directly into MT Manager. Instead of switching between multiple tools or browser tabs, you configure your API keys once and translate strings with a tap.

The plugin supports four AI providers, any OpenAI-compatible endpoint, and Google Cloud Translation, with built-in context presets, tone controls, and retry logic — making it suitable for everything from casual app localization to production-grade translation workflows.

---

## Features

### Multi-Provider Architecture

| Provider                   | Models                                             | Free Tier             |
| -------------------------- | -------------------------------------------------- | --------------------- |
| **Google Gemini**    | Gemini 3 Pro/Flash, 2.5 Flash/Pro, 2.5 Flash-Lite  | 2000 req/day (Flash)  |
| **OpenAI**           | GPT-5.2/5.1/5, GPT-4.1, GPT-4o, o3/o4 Mini         | Pay-as-you-go         |
| **Anthropic Claude** | Claude Opus 4.6/4.5, Sonnet 4.5, Haiku 4.5         | Pay-as-you-go         |
| **OpenRouter**       | 400+ models from every major lab, one key          | Free tier on select models |
| **Custom endpoint**  | Any OpenAI-compatible API                          | Depends on the service |
| **Google Cloud**     | Neural Machine Translation                         | 500K chars/month free |

> Dynamic model catalog — the plugin fetches the latest available models from each provider's API automatically. OpenRouter's catalog also shows context length and per-token price.

### Bring Your Own Endpoint

Anything that speaks OpenAI's `/chat/completions` works as a provider — just add a name, base URL and model:

| Service | Base URL |
| --- | --- |
| Groq | `https://api.groq.com/openai/v1` |
| DeepSeek | `https://api.deepseek.com/v1` |
| Together | `https://api.together.xyz/v1` |
| xAI | `https://api.x.ai/v1` |
| Ollama (local) | `http://127.0.0.1:11434/v1` |
| LM Studio (local) | `http://127.0.0.1:1234/v1` |

Local servers need no API key — leave the field empty and no `Authorization` header is sent.

### Context-Aware Translation

- **8 ready-made context presets** — Mobile App, Gaming, E-commerce, Developer Docs, and more
- **6 tone presets** — Friendly, Marketing, Legal, Support, Technical, Playful
- **Custom context fields** — App description, target audience, extra rules
- Translations adapt to your app's domain and user base

### Clean Settings UI

Organized into 5 navigable categories instead of a flat list:

```
TranslateKit Settings
├── AI Providers          → Pick the active one, configure keys and models
├── Translation Settings  → Languages, timeout, retries, batching
├── Context & Tone        → Presets, tone, audience, notes
├── Tools & Diagnostics   → Provider health, tests, logs, export/import
├── Translate This Plugin → How to add your language
└── About
```

### Interface Languages

The plugin itself speaks **English**, **Türkçe** and **简体中文**, following the
device language. Every screen reads its text from a language pack in
[`app/src/main/assets`](app/src/main/assets) — a plain `key: value` file.

Adding a language takes one file and no Java: copy `strings.mtl` to
`strings-<code>.mtl`, translate the right-hand side, open a pull request. A
missing entry falls back to English rather than breaking, so partial
translations are welcome. See **[docs/TRANSLATING.md](docs/TRANSLATING.md)**,
which the plugin links to from its own settings.

You can also choose which of the 37 translation languages appear in MT's Source
and Target dropdowns, under *Translation Settings → Languages*.

### Developer Tools

- **Provider Dashboard** — See all API key statuses at a glance
- **Interactive Provider Test** — Validate API key format instantly
- **Debug Logging** — Detailed request/response logs in MT Manager
- **Hidden Debug Menu** — Model cache diagnostics (5-tap easter egg)

---

## Quick Start

### 1. Download

Grab the latest `mt.plugin.translatekit.mtp` from [Releases](https://github.com/ilker-binzet/TranslateKit/releases).

### 2. Install

Open the `.mtp` file with MT Manager → tap **Install**.

### 3. Configure

Open the plugin settings → **AI Providers** → add your API key for at least one provider.

> **Tip:** Gemini offers a generous free tier. Get your key at [aistudio.google.com](https://aistudio.google.com/apikey).

### 4. Translate

Open any `strings.xml` in MT Manager → use the translation function → TranslateKit handles the rest.

---

## Build from Source

```powershell
# Clone
git clone https://github.com/ilker-binzet/TranslateKit.git
cd TranslateKit

# Build
.\gradlew.bat app:packageReleaseMtp

# Output
# app/build/outputs/mt-plugin/mt.plugin.translatekit.mtp
```

**Requirements:** JDK 17+, Android SDK (set path in `local.properties`)

---

## Project Structure

```
app/src/main/java/bin/mt/plugin/
├── common/
│   ├── HttpUtils.java                     # Shared HTTP client (all providers)
│   └── JSONCompat.java                    # JSON helpers for MT runtime
├── provider/
│   ├── Provider.java                      # Endpoint, credentials, wire format
│   ├── Providers.java                     # Registry: built-ins + user-defined
│   └── ProviderClient.java                # Request/response per wire format
├── gemini/
│   ├── GeminiTranslatePreference.java     # Main settings (5-category nav)
│   ├── TranslationSubPreference.java      # Engine, timeout, retries
│   ├── ContextToneSubPreference.java      # Presets, tone, audience
│   ├── ToolsSubPreference.java            # Dashboard, tests, debug
│   ├── GeminiProviderPreference.java      # Gemini provider settings
│   ├── OpenAIProviderPreference.java      # OpenAI provider settings
│   ├── ClaudeProviderPreference.java      # Claude provider settings
│   ├── OpenRouterProviderPreference.java  # OpenRouter provider settings
│   ├── CustomProviderPreference.java      # User-defined endpoints
│   ├── GeminiTranslationEngine.java       # Core translation engine
│   ├── GeminiConstants.java               # All constants & model names
│   ├── GeminiColorTokens.java             # Theme-aware UI colors
│   ├── ModelCatalogManager.java           # Dynamic model fetching & cache
│   ├── ProviderCatalogRefresher.java      # Background catalog refresh
│   └── TranslationDebugLogger.java        # Structured debug logging
└── google/
    └── GoogleCloudTranslationEngine.java  # Google Cloud NMT fallback
```

**Adding a provider.** Providers are many; wire formats are three. `ProviderClient`
dispatches on `Provider.wire` (`openai` / `anthropic` / `gemini`) rather than on
provider identity, so a new OpenAI-compatible service is a row in `Providers`,
not a new code path.

---

## Configuration Reference

| Setting           | Default     | Description                         |
| ----------------- | ----------- | ----------------------------------- |
| Default AI Engine | Gemini      | Which provider handles translations |
| Request Timeout   | 30000 ms    | Max wait time per API call          |
| Max Retries       | 2           | Retry attempts on failure           |
| Tone & Voice      | *(empty)* | Writing style guidance for AI       |
| App Description   | *(empty)* | App name and type context           |
| Target Audience   | *(empty)* | Who uses your app                   |
| Debug Logging     | Off         | Verbose request/response logs       |

---

## Supported Languages

Arabic, Chinese (Simplified/Traditional), Czech, Danish, Dutch, English, Finnish, French, German, Greek, Hebrew, Hindi, Hungarian, Indonesian, Italian, Japanese, Korean, Malay, Norwegian, Persian, Polish, Portuguese (BR/PT), Romanian, Russian, Slovak, Spanish, Swedish, Thai, Turkish, Ukrainian, Vietnamese, and more.

---

## Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Push and open a Pull Request

---

## License

[MIT License](LICENSE) — Copyright (c) 2025 Ilker Binzet

---

<div align="center">

**[Report a Bug](https://github.com/ilker-binzet/TranslateKit/issues) · [Request a Feature](https://github.com/ilker-binzet/TranslateKit/issues)**

Made with care for the MT Manager community.

</div>
