# Translating TranslateKit

The plugin ships in English, Turkish and Simplified Chinese. Adding your own
language takes one file and no Java.

## What a language pack is

Every screen reads its text from a **language pack** in
[`app/src/main/assets/`](../app/src/main/assets). A pack is a plain text file,
one `key: value` per line:

```
nav_providers: AI Providers
status_ready: Ready
prov_refresh_models: Refresh Model List
```

`strings.mtl` is the base pack and defines every entry. The others translate it:

| File | Language |
|---|---|
| `strings.mtl` | English — the base, always complete |
| `strings-tr.mtl` | Turkish |
| `strings-zh-CN.mtl` | Simplified Chinese |

## Adding a language

1. Copy `strings.mtl` to `strings-<code>.mtl`, where `<code>` is the language
   code MT reports for the device — `de`, `ru`, `pt-BR`, `zh-TW`.
2. Translate the text on the right of each colon. **Leave the keys alone.**
3. Open a pull request.

MT looks for the most specific pack first and falls back: `strings-pt-BR.mtl`,
then `strings-pt.mtl`, then `strings.mtl`. **A missing entry falls back to
English rather than breaking**, so a partial translation is welcome. Send what
you have.

## Rules that matter

**Keep `\n` where it appears.** It is a line break in the app.

```
prov_test_result: Original: Hello\n\nTranslation:\n
```

**Keep the emoji.** They are how a row is recognised at a glance, and several
screens read the coloured circle to decide what colour to paint the status:
`🟢` means ready, `🔴` means broken, `🟡` means a warning. Changing one changes
the colour on screen.

**Leave technical terms alone.** `API key`, `endpoint`, `token`, `JSON`, model
names like `gpt-5.3-nano`, and provider names like OpenRouter are not
translated — people search for them in that form.

**Two entries are prompts, not labels.** `prov_test_prompt` and
`prov_test_result` are the text of the quick API test. Write them so the test
translates *into your language*:

```
prov_test_prompt: Translate to German: Hello
prov_test_result: Original: Hello\n\nTranslation:\n
```

**Watch the length.** These are preference rows on a phone. A summary that runs
past two lines gets cut off.

## What is deliberately not translated

- The hidden debug menu (tap the version five times). It is a developer aid.
- Rare error text: quota exhausted, model retired, connection refused. It
  quotes provider terminology and is almost never seen.
- Prompt text behind the Context & Tone presets. That is sent to the model,
  which handles English most reliably.

If you think something in those groups should be translated after all, say so
in the pull request.

## Checking your work

```
./gradlew testDebugUnitTest
```

`LanguagePackTest` fails if a pack declares an entry the base pack does not —
almost always a typo in a key, which would silently do nothing. It also fails
if a screen asks for an entry nothing defines, which would reach the user as a
raw `{some_key}`.

You do not need an Android device or any API key to run it.

## Where the entries come from

If you are changing the code rather than translating it: screens call
`str("{key}")`, and the same test insists that every key called for exists and
that every entry defined is called for. Add the entry to `strings.mtl` in the
same commit as the code that asks for it, and translations can follow later.
