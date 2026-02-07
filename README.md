# TranslateKit

**Multi-provider translation plugin for MT Manager**

![Version](https://img.shields.io/badge/version-0.1.0--dev-orange)
![License](https://img.shields.io/badge/license-MIT-green)

`TranslateKit` exposes Google Gemini 3, OpenAI GPT-5/o3, Anthropic Claude 4.5, and Google Cloud Translation engines to MT Manager through a single plugin. Built on top of the new **SDK 3** toolchain.

---

## Highlights

- One plugin, three AI providers (Gemini, OpenAI, Claude)
- Auto language detection with 38+ targets
- Context presets and tone controls for concise translations
- Built-in API key validation, retry logic, and structured logging
- Ships as a single `mt.plugin.translatekit.mtp` artifact

---

## Requirements

- MT Manager [(latest release)](https://mt2.cn/download/)
- API keys for the providers you intend to use
- JDK 17, and the Gradle wrapper bundled in this repo

---

## Installation

### Option A – Download the release asset
1. Grab the latest `.mtp` from [Releases](https://github.com/ilker-binzet/TranslateKit/releases).
2. In MT Manager, open **Plugins → Install Plugin** or select the downloaded file, and open the file with MT Manager then install it.

### Option B – Build it yourself

```powershell
git clone https://github.com/ilker-binzet/TranslateKit.git
cd TranslateKit
.\gradlew.bat app:packageReleaseMtp
```

The generated file lives at `app\build\outputs\mt-plugin\mt.plugin.translatekit.mtp`. `build-plugin.bat` runs the same command plus a clean step on Windows.

---

## Building & Testing

1. Configure `local.properties` with your Android SDK path if needed.
2. Run the Gradle wrapper (`./gradlew` on Linux/macOS) so that the correct version is used everywhere.
3. Load the resulting `.mtp` into MT Manager for a manual smoke test. The settings screen exposes Gemini/OpenAI/Claude cards, connection checks, and debug logging toggles.

---

## Release Checklist

1. Update `versionCode` / `versionName` in `app/build.gradle` and `GeminiConstants`.
2. Run `gradlew app:packageReleaseMtp` (or `build-plugin.bat`).
3. Upload `app/build/outputs/mt-plugin/mt.plugin.translatekit.mtp` to a GitHub Release.
4. Tag the commit with the semantic version (e.g., `v0.1.0`).

---

## Development Notes

- Main sources live under `app/src/main/java/bin/mt/plugin/gemini`.
- `GeminiTranslationEngine` coordinates provider selection, retries, and logging.
- Provider preference screens (`GeminiTranslatePreference`, `OpenAITranslatePreference`, etc.) are standard MT Manager UI classes.
- Language strings sit in `app/src/main/assets/*.mtl` files; add entries there before referencing new text in code.

---

## License

This project is available under the [MIT License](LICENSE).

---

## Support

Please use [GitHub Issues](https://github.com/ilker-binzet/TranslateKit/issues) for bugs or feature requests.
