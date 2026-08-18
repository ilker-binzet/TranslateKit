# Provider Architecture Rework

Date: 2026-08-17
Status: Approved
Target version: 0.5.0

## Problem

Adding a fourth translation provider currently costs nine new methods and
eight `switch` updates.

Provider request/response handling is duplicated across three classes:

| Class | Duplicated methods |
| --- | --- |
| `GeminiTranslationEngine` | `translateWithGemini`, `translateWithOpenAI`, `translateWithClaude` |
| `AITranslateFloatingMenu` | `translateWithGemini`, `translateWithOpenAI`, `translateWithClaude` |
| `AITranslateToolMenu` | `translateWithGemini`, `translateWithOpenAI`, `translateWithClaude` |

The provider identifier is branched on in eight places: `GeminiTranslationEngine`
(`onStart`, `translateSingle`, `batchTranslate`), `AITranslateFloatingMenu` (two
sites), `AITranslateToolMenu` (two sites), `GeminiColorTokens`, and
`GeminiTranslatePreference`.

Provider configuration lives in flat parallel fields — `openAiApiKey`,
`claudeApiKey`, `openAiModel`, `claudeModel`, `openAiEndpoint`, `claudeEndpoint`
— so each new provider adds another set.

## Key observation

Providers are many; wire formats are three.

| Wire format | Request shape | Auth | Response path |
| --- | --- | --- | --- |
| `openai` | `{model, messages[], temperature, max_tokens}` | `Authorization: Bearer` | `choices[0].message.content` |
| `anthropic` | `{model, max_tokens, system, messages[]}` | `x-api-key` + `anthropic-version` | `content[].text` |
| `gemini` | `{contents[], generationConfig}` | key in query string | `candidates[0].content.parts[].text` |

OpenRouter, Groq, DeepSeek, Together, Mistral, xAI, Cerebras, Ollama and
LM Studio all speak the `openai` wire format. Dispatching on wire format rather
than on provider identity makes those providers a configuration row instead of
a code path.

## Design

### New package: `bin.mt.plugin.provider`

**`Provider.java`** — immutable value object, no Android dependencies.

```
id              String   stable key, e.g. "openai", "openrouter", "custom:groq"
displayName     String   UI label
wire            String   WIRE_OPENAI | WIRE_ANTHROPIC | WIRE_GEMINI
endpoint        String   resolved chat/completions URL
apiKey          String   resolved secret
model           String   resolved model id
keyPattern      String   nullable; null disables format validation
accentDark      int      ARGB
accentLight     int      ARGB
modelsEndpoint  String   nullable; catalog fetch URL
extraHeaders    Map      nullable; OpenRouter attribution headers
```

**`Providers.java`** — registry. Android-facing.

- `List<Provider> all(SharedPreferences)` — built-ins plus user-defined.
- `Provider selected(SharedPreferences)` — resolves `PREF_DEFAULT_ENGINE`,
  falling back to Gemini as today.
- `Provider byId(SharedPreferences, String)`.

Built-ins: `gemini`, `openai`, `claude`, `openrouter`.

**`ProviderClient.java`** — wire dispatch. Pure functions, no Android imports,
no retry logic, no logging.

- `String url(Provider)` — Gemini embeds model and key; others return `endpoint`.
- `Map<String,String> headers(Provider)`.
- `JSONObject buildRequest(Provider, String prompt, String systemPrompt)`.
- `String parseResponse(Provider, JSONObject)`.

`executeWithRetry`, `TranslationDebugLogger`, placeholder tokenisation and batch
parsing stay in `GeminiTranslationEngine` — they are provider-agnostic already
and are out of scope for this change.

### Call-site changes

`GeminiTranslationEngine.onStart` replaces the three-case `switch` and six
provider fields with one `Provider provider = Providers.selected(prefs)`.
`translateSingle` and `batchTranslate` replace their `switch` blocks with a
single `executeWithRetry(...)` wrapping `ProviderClient` calls.

`AITranslateFloatingMenu` and `AITranslateToolMenu` delete their six duplicate
methods and call `ProviderClient` directly.

`GeminiColorTokens.accentFor` reads the colour from the `Provider` rather than
matching on a lowercased string.

The Claude fallback-model retry (`translateWithClaudeWithFallback`,
`trySwitchClaudeFallbackModel`) is preserved, generalised to any provider with a
declared fallback model.

### OpenRouter

| Field | Value |
| --- | --- |
| Endpoint | `https://openrouter.ai/api/v1/chat/completions` |
| Models | `https://openrouter.ai/api/v1/models` — no authentication required |
| Key pattern | `^sk-or-v1-[A-Za-z0-9]{16,}$` |
| Extra headers | `HTTP-Referer`, `X-Title` (attribution, optional) |

The models endpoint returns `context_length` and `pricing.prompt` /
`pricing.completion` per model. `ModelCatalogManager.fetchOpenRouterModels` uses
these to populate `ModelInfo.detail` with context size and per-million-token
price. No other provider exposes this, so the field stays free-text.

### User-defined OpenAI-compatible providers

One preference key, `custom_providers`, holds a JSON array:

```json
[{"name": "Groq", "baseUrl": "https://api.groq.com/openai/v1", "apiKey": "...", "model": "..."}]
```

Each entry becomes a `Provider` with `wire = WIRE_OPENAI`, `id = "custom:<slug>"`
and `keyPattern = null` (self-hosted endpoints such as Ollama and LM Studio take
no key, so format validation must not reject an empty value).

Scope limit: add, edit and delete only. No reordering, no per-provider timeout
or retry overrides, no import/export.

### Preference compatibility

Built-in `Provider` entries point at the existing preference keys
(`gemini_api_key`, `openai_api_key`, `openai_model_name`, `claude_api_key`, and
so on). No key is renamed, so installed users keep their configuration and no
migration code is needed.

## Repository cleanup

Bundled into the same release because it touches the same files.

1. Rewrite two commit messages (`8c37d4a`, `0566036`) that name AI tooling, and
   scrub the corresponding `.gitignore` blob entries, using `git filter-repo`.
   A `git bundle` backup is taken first. All commit hashes change; the push is
   `--force-with-lease`. Existing forks retain the old history — accepted.
2. Local-only tool directories go in `.git/info/exclude`, never in the tracked
   `.gitignore`, so the ignore list itself carries no signal.
3. `GeminiConstants`: replace the placeholder `@author MT Manager Plugin
   Developer` with the real author and delete the 62 unused `ICON_*` constants
   (lines 244-327; zero references across the codebase).
4. Replace the leftover `https://github.com/yourusername/mt-google-translate-plugin`
   URL with the real repository URL.
5. `app/build.gradle`: rename the Android `namespace` from the demo-template
   value `bin.mt.plugin.pusher` to `bin.mt.plugin.translatekit`. This is the
   Android manifest namespace only — the MT plugin's `interfaces` and
   `mainPreference` entries reference Java package names and are unchanged.

## Model catalogue

Refresh the `GEMINI_SEED`, `OPENAI_SEED` and `CLAUDE_SEED` tables, add an
`OPENROUTER_SEED`, and confirm each provider's live fetch path in
`ProviderCatalogRefresher` still resolves.

## Verification

`ProviderClient` and the registry hold branching logic that can break silently:
a wrong wire format for a provider produces a confusing API error rather than an
obvious failure. One JUnit file, `app/src/test/java/bin/mt/plugin/provider/ProviderClientTest.java`,
covers:

- wire format resolves correctly for each built-in provider;
- `url(Provider)` embeds model and key for Gemini and returns the raw endpoint
  otherwise;
- `headers(Provider)` sets `Authorization` for the OpenAI wire and both
  `x-api-key` and `anthropic-version` for the Anthropic wire;
- the OpenRouter key pattern accepts `sk-or-v1-…` and rejects `sk-…`;
- a custom provider with an empty key is accepted (`keyPattern == null`).

Risk: `bin.mt.json` is supplied by the MT plugin Gradle plugin and may not be on
the unit-test classpath. The assertions above are deliberately confined to
strings, regular expressions and maps so the test compiles without it. If
`bin.mt.json` does resolve, request-body assertions are added; if the test
source set cannot be made to compile cheaply, this is reported rather than
worked around, and the checks move to an assertion-based `main` entry point.

Manual check, since no emulator is in the loop: build the `.mtp`, install it,
and translate a small `strings.xml` with each of Gemini, OpenAI, Claude,
OpenRouter and one custom endpoint.

## Out of scope

- Renaming the `bin.mt.plugin.gemini` Java package.
- Splitting the remaining 1496 lines of `GeminiTranslationEngine`.
- Glossary, translation memory and cost tracking.
