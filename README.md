# AI Translation Hub

**Multi-provider translation plugin for MT Manager**

![Version](https://img.shields.io/badge/version-0.1.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)

`AI Translation Hub` exposes Google Gemini, OpenAI GPT-4o, and Anthropic Claude engines to MT Manager through a single plugin. It is also the first publicly available MT Manager plugin built on top of the new **SDK 3** toolchain, showcasing what the refreshed platform can do.

---

## Highlights

- One plugin, three AI providers (Gemini, OpenAI, Claude)
- Auto language detection with 38+ targets
- Context presets and tone controls for concise translations
- Built-in API key validation, retry logic, and structured logging
- Ships as a single `mt.plugin.ai.hub.mtp` artifact (same name as `pluginID`)

---

## Requirements

- MT Manager [(latest release)](https://mt2.cn/download/)
- API keys for the providers you intend to use
- JDK 17, and the Gradle wrapper bundled in this repo

---

## Installation

### Option A – Download the release asset
1. Grab the latest `.mtp` from [Releases](https://github.com/ilker-binzet/AI-Translation-Hub/releases).
2. In MT Manager, open **Plugins → Install Plugin** or select the downloaded file, and open the file with MT Manager then install it.
3. The installer expects the file to be named `mt.plugin.ai.hub.mtp`; renaming to `AITranslationHub_v0.1.0.mtp` is optional.

### Option B – Build it yourself

```powershell
git clone https://github.com/ilker-binzet/AI-Translation-Hub.git
cd ai-translation-hub
.\gradlew.bat app:packageReleaseMtp
```

The generated file lives at `app\build\outputs\mt-plugin\mt.plugin.ai.hub.mtp`. `build-plugin.bat` runs the same command plus a clean step on Windows.

---

## Building & Testing

1. Configure `local.properties` with your Android SDK path if needed.
2. Run the Gradle wrapper (`./gradlew` on Linux/macOS) so that the correct version is used everywhere.
3. Load the resulting `.mtp` into MT Manager for a manual smoke test. The settings screen exposes Gemini/OpenAI/Claude cards, connection checks, and debug logging toggles.

---

## Release Checklist

1. Update `versionCode` / `versionName` in `app/build.gradle` and `GeminiConstants`.
2. Run `gradlew app:packageReleaseMtp` (or `build-plugin.bat`).
3. Upload `app/build/outputs/mt-plugin/mt.plugin.ai.hub.mtp` to a GitHub Release.
4. Tag the commit with the semantic version (e.g., `v0.1.0`).

The GitHub Actions workflow (`release.yml`) builds the same artifact on every push to `main` and attaches it to releases, ensuring the published file always matches the tagged source.

### Fresh repository setup

If you are starting from a clean working tree, the following sequence creates the initial history and pushes the `v0.1.0` tag:

```powershell
git init
git add .
git commit -m "chore: bootstrap v0.1.0"
git branch -M main
git remote add origin <your-repo-url>
git tag v0.1.0
git push -u origin main
git push origin v0.1.0
```

Adjust the remote URL or tag name when preparing future releases.

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

Please use [GitHub Issues](https://github.com/ilker-binzet/AI-Translation-Hub/issues) for bugs or feature requests.
