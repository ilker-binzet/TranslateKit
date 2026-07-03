# Changelog

All notable changes to TranslateKit will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.2] - 2026-07-02

### Fixed
- **Batch translation silently skipping strings.** Three compounding causes:
  1. `maxOutputTokens` was hard-coded to 2048 for Gemini and OpenAI — large
     batches were truncated mid-list (and on Gemini 2.5+ "thinking" models the
     hidden reasoning tokens also count against the cap). Output budgets now
     scale with the prompt size (Gemini 4096–32768, OpenAI/Claude 2048–8192).
  2. Items the model dropped or whose placeholder validation failed were
     silently kept as originals. They are now **retried individually**, and
     only kept as originals if the individual retry also fails.
  3. Warnings/errors (missing items, retries, failures) only reached the MT
     log when debug logging was enabled — they are now always logged.
- **Batch translation stalling mid-run.** The retry backoff honoured server
  `Retry-After` headers without a cap (a 429 could put the engine to sleep
  for an hour); waits are now capped at 60 s and always logged. The user's
  Request Timeout setting was read but never applied — `HttpUtils` now
  applies it per call instead of a fixed 120 s.
- **Model Catalog appearing dead/hanging in Preferences.** The "Model
  Catalog" row had no click handler at all; it now opens an instant, offline
  dialog listing all cached + curated models (active model and recommended
  ones marked) on the Gemini, OpenAI and Claude pages. The Gemini
  "Refresh Model List" action now shows a loading dialog while fetching,
  like the OpenAI/Claude pages already did.

## [0.4.1] - 2026-07-02

### Fixed
- **Translation failed with `NoSuchMethodError: RequestBody.create(String, MediaType)`
  on-device.** Root cause: the project declared OkHttp **4.12.0**, but MT
  Manager supplies its own OkHttp **3.12.13** (the version the SDK declares
  transitively) from the host classloader, which always wins at runtime.
  Code compiled against the 4.x-only `RequestBody.create(String, MediaType)`
  overload therefore crashed on the host's 3.12 classes. Fix: removed the
  OkHttp dependency entirely (the SDK's transitive 3.12.13 is now the
  compile-time API, matching the runtime exactly) and switched `HttpUtils`
  to the 3.x-compatible `RequestBody.create(MediaType, String)`. Side
  benefit: the `.mtp` shrank from ~431 KB to ~93 KB because the bundled
  (and unusable) OkHttp 4 + Kotlin stdlib are gone.
- **Crash when saving/testing a Gemini API key.** Same root cause — but it
  crashed instead of failing gracefully because `NoSuchMethodError` is an
  `Error`, which `catch (Exception)` cannot intercept, so the background
  test thread killed the whole MT Manager process. All 8 background-thread
  boundaries (provider key tests, quick-translate tests, model-catalog
  refresh) now catch `Throwable` and surface the failure in the UI instead.

## [0.4.0] - 2026-07-02

### Changed
- **Completed the MT Plugin SDK v3 stable migration.** The localization layer
  now uses the stable convention: strings live in `assets/strings.mtl`
  (+ `strings-tr.mtl` for Turkish) and are referenced via `{key}`
  placeholders / `context.getString("{key}")`. All 41 usages of the
  deprecated `getAssetLocalString(String)`, `getLocalString()` and
  `Builder.setLocalString(LocalString)` APIs across 14 source files were
  removed — the plugin now compiles with **zero deprecation warnings**
  against SDK 3.0.0.
- **Merged string assets.** `GeminiTranslate.mtl` and `GoogleTranslate.mtl`
  (and their `-tr` variants) were merged into `strings.mtl` /
  `strings-tr.mtl`. Google Cloud engine keys are namespaced with a `gc_`
  prefix (e.g. `gc_plugin_name`) to avoid collisions with the 39 keys the
  two files used to share with different values. The leftover demo
  placeholder content that previously occupied `strings.mtl` is gone.
- **Localized plugin metadata.** The `mtPlugin` block now uses
  `name = "{plugin_name}"` and `description = "{plugin_description}"`, so
  the plugin name/description shown in MT Manager follow the user's
  language, matching the official SDK v3 modules.

### Removed
- `GeminiHttpUtils.java.bak` and `google/HttpUtils.java.bak` leftovers.
- The four legacy per-engine `.mtl` asset files.

### Fixed
- Restored the corrupted Chinese comment (`??????`) above `versionName`
  in `app/build.gradle`.

## [0.4.0-beta4] - 2026-06-19

### Changed
- **Migrated JSON library** from Android's `org.json` to the SDK 3.0
  `bin.mt.json` library. Every JSON call site (engines, provider preferences,
  floating menu, tool menu, export/import, dashboard) now uses the SDK's
  `bin.mt.json.JSONObject` / `JSONArray`. The `has(String)` calls became
  `contains(String)`; the `keys()` / `length()` calls became
  `names()` / `size()`; the `toString(int)` pretty-print indent became
  `toString(PrettyPrint.indentWithSpaces(n))`. A thin
  `bin.mt.plugin.common.JSONCompat` shim preserves the original
  `optXxx(obj, key, default)` ergonomics for the dozens of read sites.
- **Consolidated HTTP utilities** into a single
  `bin.mt.plugin.common.HttpUtils` built on **OkHttp 4.12.0** (matching the
  v3 demo's reference implementation). Replaces the bespoke
  `GeminiHttpUtils` and `google/HttpUtils` (both deleted) which had
  duplicated URL-connection plumbing, manual `Retry-After` parsing, and
  per-request timeouts. The new helper exposes three methods:
  - `postJson(url, body, headers)` — POST a JSONObject and return one
  - `getJson(url, headers)` — GET and return a JSONObject
  - `getString(url, headers)` — GET a raw string
  All return shared types (`bin.mt.json.JSONObject` / `String`) and honour
  HTTP `Retry-After` headers with exponential backoff.
- **Clipboard export** — exporting settings now copies the JSON preset to
  the clipboard automatically, in addition to opening the dialog. Users no
  longer have to long-press to copy. The clipboard write is wrapped in a
  `try/catch` so older MT Manager builds that don't expose
  `setClipboardText(text, label)` fall back to the dialog-only path.

### Removed
- `bin.mt.plugin.gemini.GeminiHttpUtils` — replaced by
  `bin.mt.plugin.common.HttpUtils`.
- `bin.mt.plugin.google.HttpUtils` — replaced by
  `bin.mt.plugin.common.HttpUtils`.
- `import org.json.*` and `import org.json.JSONException` from every
  source file. The new SDK ships its own JSON library which is now the
  only one in use.

## [0.4.0-beta3] - 2026-06-19

### Changed
- **Provider-only UI**: model names are no longer shown in the settings screens.
  The model selection dropdown has been removed from all three provider preferences
  (`GeminiProviderPreference`, `OpenAIProviderPreference`,
  `ClaudeProviderPreference`) and the main-settings Claude sub-page
  (`ClaudeTranslatePreference`). Instead each provider preference now shows a
  "Provider" + "Model" info row. The model is the provider's default
  (recommended) one; power users can override it via the existing "Custom Model"
  input field. The main-settings summary now reads "Provider: …" instead of
  "Engine: …".
- Engine `onStart()` now resolves the active model via
  `ProviderCatalogRefresher.resolveSelectedModel(...)`, so a Custom Model
  override always wins over the saved (legacy) list selection, which in turn
  wins over the hard-coded default.

## [0.4.0-beta2] - 2026-06-19

### Added
- **Auto-refreshing model catalog** — the three provider preference screens
  (Gemini, OpenAI, Claude) now silently refresh the model list from each
  provider's API in the background when the cache is empty or older than 6
  hours. The manual "Refresh" button still works for power users. New models
  appear automatically — no plugin update required.
- **Custom Model override** — every provider preference has a new "Custom
  Model" input field. Type any model name (e.g. `gemini-2.0-flash-exp`,
  `gpt-5.3-nano`, `claude-opus-5-latest`) and it takes precedence over the
  dropdown selection. Solves the "new model released before plugin update"
  problem permanently.
- **"Last refreshed" indicator** on each provider preference shows when the
  catalog was last synced (e.g. "Last refreshed: 2 h ago" / "Never refreshed
  (using default list)").

### Changed
- Model id constants moved out of provider preference files into a single
  structured `GEMINI_SEED` / `OPENAI_SEED` / `CLAUDE_SEED` table in
  `GeminiConstants`. Each row is `{id, displayName, description, recommended, priority}`.
  The live API list is merged on top of this seed by id, so seed entries
  fill gaps when offline or first-install.
- Naming/priority logic in `ModelCatalogManager` is now family-pattern based
  (regex on `gpt-X`, `oN`, `claude-{tier}-{ver}`, `gemini-X.Y-{tier}`).
  Previously hardcoded mappings like "if id starts with sonnet-4-5" are
  replaced with patterns that also match future variants (sonnet-4-6,
  sonnet-5, opus-5, gemini-4, gpt-6, o5…) without any code change.
- Provider preference files: `GeminiProviderPreference`,
  `OpenAIProviderPreference`, `ClaudeProviderPreference`,
  `ClaudeTranslatePreference` all share a new helper
  `ProviderCatalogRefresher` that handles the render/refresh/merge logic
  consistently.

### Notes
- Auto-refresh only fires when (a) the cache is empty or older than 6h, AND
  (b) an API key is already saved. First install with no API key → shows
  the curated seed defaults, no network call.
- Custom model field takes priority over the dropdown list when non-empty.
  Clearing it reverts to the dropdown selection.

## [0.4.0-beta] - 2026-06-18

### Changed
- **Migrated to MT Plugin SDK v3 (3.0.0 stable)** — up from 1.0.0-beta5
- Engine configuration now set via `onBuildConfiguration(...)` override (the SDK's
  recommended pattern), inheriting `autoRepairFormatSpecifiersError = true` which
  silently fixes common `%s` → `％S` placeholder mis-translations.
- Enabled `setAllowBatchTranslationBySeparator(true)` and
  `setMaxTranslationTextLength(10000)` so MT Manager can auto-batch entries and
  safely split oversized strings. Our existing `batchTranslate()` path is unaffected.
- Android `namespace` set to the official `bin.mt.plugin.pusher` (previously
  `bin.mt.plugin.demo`, which was the demo placeholder).
- Added `proguard-android-optimize.txt` to the default proguard files for better
  release shrink/obfuscation.

### Removed
- `repackReleaseApk` Gradle task — was a beta-era workaround to bypass APK
  signing verification. No longer needed in v3 stable; the upstream
  `packageReleaseMtp` task now produces a directly-installable mtp.
- Unused `java.nio.file.*` and `java.util.zip.*` imports from the removed task.

### Notes
- `targetSdk` stays at 28 (matches every official MT translator module). This is
  intentional — MT Manager supports a wide range of Android versions, and bumping
  targetSdk could exclude older devices used by community translators.
- **No breaking changes for users.** API keys, model preferences, context
  presets — all persisted values remain compatible.

## [0.3.0-alpha] - 2026-03-11

### Added
- Bilingual output mode: keep original text with translation below it
- User-configurable batch size and max characters preferences
- Placeholder hardening for %s, %1$s, {0} patterns
- Rate limit Retry-After header support
- Preset import/export for translation settings
- Batch debug logging and parse count validation
- Hebrew language code fix (he → iw)
- Claude max_tokens parameter fix
- Batch-to-individual fallback on IOException

### Changed
- Upgraded MT Plugin SDK from v3 beta3 to v3 beta5
- Custom SimpleBatchingStrategy replaces DefaultBatchingStrategy
- Multi-format batch response parser with [N], N., N) support

---

## [0.2.2-alpha] - 2026-02-18

### Added
- Bilingual output mode: keep original text with translation below it
- User-configurable batch size via SharedPreferences (gemini_batch_size, google_batch_size)
- Batch max characters preference for both engines
- Input validation with safe fallback to defaults for invalid batch config values

### Changed
- Upgraded MT Plugin SDK from v3 beta3 to v3 beta5
- Adapted to new SDK API: translate() is now final, engines use translateSingle() internally
- Custom SimpleBatchingStrategy replaces package-private DefaultBatchingStrategy
- Fixed potential infinite recursion between translate() and batchTranslate() in SDK beta5

### Fixed
- Placeholder corruption during translation (%s, %1$s, {0} etc. now protected via tokenization)
- Batch translation skipping/dropping strings (multi-format response parser with [N], N., N) support)
- Special character breakage causing formatting issues or skipped strings
- Added batch-to-individual fallback on IOException for resilient translation

## [0.2.0-alpha] - 2026-02-12

### Added
- BaseBatchTranslationEngine integration for both Gemini and Google Cloud engines
- Gemini batch translation with numbered [N] text format and AI-based batching
- Google Cloud native POST JSON batch translation with retry logic
- Dynamic model catalog with latest AI model support (GPT-4.1, Claude Sonnet 4, Gemini 3.x)
- API key format validation with warnings at engine init time
- Format hints in quick test dialog per provider

### Changed
- Upgraded from BaseTranslationEngine to BaseBatchTranslationEngine (SDK v3 beta3)
- Updated ModelCatalogManager with current OpenAI, Claude, and Gemini model priorities
- Fixed display names in ToolsSubPreference (OpenAI GPT, Claude AI)
- Added .trim() to all API key reads across engines and preference screens

### Fixed
- API keys with leading/trailing whitespace now handled correctly
- Model priority ordering for latest generation models

## [0.1.0] - 2026-02-10

### Added
- Multi-provider translation engine (Gemini, OpenAI, Claude, Google Cloud)
- SDK 3.0.0-beta3 support
- Auto language detection with 38+ target languages
- Context presets and tone controls
- API key validation and retry logic
- Structured debug logging
- Türkçe lokalizasyon desteği
- GitHub Actions CI/CD workflow
