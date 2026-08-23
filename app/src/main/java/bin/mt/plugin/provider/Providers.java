package bin.mt.plugin.provider;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bin.mt.json.JSONArray;
import bin.mt.json.JSONObject;
import bin.mt.plugin.common.JSONCompat;
import bin.mt.plugin.gemini.GeminiConstants;
import bin.mt.plugin.gemini.ProviderCatalogRefresher;

/**
 * Resolves the configured providers from preferences.
 *
 * <p>Built-in entries reuse the preference keys that shipped in earlier
 * versions, so an upgrade keeps every configured key and model without a
 * migration step.
 *
 * @author Ilker Binzet
 */
public final class Providers {

    public static final String ID_GEMINI = GeminiConstants.ENGINE_GEMINI;
    public static final String ID_OPENAI = GeminiConstants.ENGINE_OPENAI;
    public static final String ID_CLAUDE = GeminiConstants.ENGINE_CLAUDE;
    public static final String ID_OPENROUTER = GeminiConstants.ENGINE_OPENROUTER;
    public static final String ID_CUSTOM_PREFIX = "custom:";

    /** Stored as {"items":[{name, baseUrl, apiKey, model}, ...]}. */
    public static final String PREF_CUSTOM_PROVIDERS = "custom_providers";

    private static final String FIELD_ITEMS = "items";

    private Providers() {
        throw new AssertionError("Cannot instantiate");
    }

    /** The provider the user picked, falling back to Gemini exactly as before. */
    public static Provider selected(SharedPreferences prefs) {
        String id = prefs.getString(GeminiConstants.PREF_DEFAULT_ENGINE,
                GeminiConstants.DEFAULT_ENGINE);
        Provider p = byId(prefs, id);
        return p != null ? p : gemini(prefs);
    }

    /** Null when no configured provider carries this id. */
    public static Provider byId(SharedPreferences prefs, String id) {
        if (id == null) {
            return null;
        }
        for (Provider p : all(prefs)) {
            if (id.equals(p.id)) {
                return p;
            }
        }
        return null;
    }

    public static List<Provider> all(SharedPreferences prefs) {
        List<Provider> list = new ArrayList<>();
        list.add(gemini(prefs));
        list.add(openai(prefs));
        list.add(claude(prefs));
        list.add(openrouter(prefs));
        list.addAll(custom(prefs));
        return list;
    }

    public static Provider gemini(SharedPreferences prefs) {
        return new Provider(
                ID_GEMINI,
                "Google Gemini",
                Provider.WIRE_GEMINI,
                GeminiConstants.API_BASE_URL,
                trim(prefs.getString(GeminiConstants.PREF_API_KEY, "")),
                ProviderCatalogRefresher.resolveSelectedModel(
                        prefs, GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL),
                GeminiConstants.API_KEY_PATTERN,
                GeminiConstants.API_BASE_URL,
                null);
    }

    public static Provider openai(SharedPreferences prefs) {
        return new Provider(
                ID_OPENAI,
                "OpenAI",
                Provider.WIRE_OPENAI,
                prefs.getString(GeminiConstants.PREF_OPENAI_ENDPOINT,
                        GeminiConstants.DEFAULT_OPENAI_ENDPOINT),
                trim(prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, "")),
                ProviderCatalogRefresher.resolveSelectedModel(
                        prefs, GeminiConstants.PREF_OPENAI_MODEL,
                        GeminiConstants.DEFAULT_OPENAI_MODEL),
                GeminiConstants.OPENAI_API_KEY_PATTERN,
                "https://api.openai.com/v1/models",
                null);
    }

    public static Provider claude(SharedPreferences prefs) {
        return new Provider(
                ID_CLAUDE,
                "Anthropic Claude",
                Provider.WIRE_ANTHROPIC,
                prefs.getString(GeminiConstants.PREF_CLAUDE_ENDPOINT,
                        GeminiConstants.DEFAULT_CLAUDE_ENDPOINT),
                trim(prefs.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "")),
                ProviderCatalogRefresher.resolveSelectedModel(
                        prefs, GeminiConstants.PREF_CLAUDE_MODEL,
                        GeminiConstants.DEFAULT_CLAUDE_MODEL),
                GeminiConstants.CLAUDE_API_KEY_PATTERN,
                GeminiConstants.CLAUDE_MODELS_ENDPOINT,
                null);
    }

    public static Provider openrouter(SharedPreferences prefs) {
        Map<String, String> attribution = new HashMap<>();
        // Both are optional; OpenRouter shows them on the account activity page.
        attribution.put("HTTP-Referer", "https://github.com/ilker-binzet/TranslateKit");
        attribution.put("X-Title", "TranslateKit");
        return new Provider(
                ID_OPENROUTER,
                "OpenRouter",
                Provider.WIRE_OPENAI,
                prefs.getString(GeminiConstants.PREF_OPENROUTER_ENDPOINT,
                        GeminiConstants.DEFAULT_OPENROUTER_ENDPOINT),
                trim(prefs.getString(GeminiConstants.PREF_OPENROUTER_API_KEY, "")),
                ProviderCatalogRefresher.resolveSelectedModel(
                        prefs, GeminiConstants.PREF_OPENROUTER_MODEL,
                        GeminiConstants.DEFAULT_OPENROUTER_MODEL),
                GeminiConstants.OPENROUTER_API_KEY_PATTERN,
                GeminiConstants.OPENROUTER_MODELS_ENDPOINT,
                attribution);
    }

    /**
     * User-defined OpenAI-compatible endpoints.
     *
     * <p>keyPattern is null throughout: self-hosted servers such as Ollama and
     * LM Studio accept no key at all, so format validation must never reject a
     * blank value here.
     */
    public static List<Provider> custom(SharedPreferences prefs) {
        List<Provider> list = new ArrayList<>();
        for (JSONObject e : customEntries(prefs)) {
            String name = JSONCompat.optString(e, "name", "");
            String baseUrl = JSONCompat.optString(e, "baseUrl", "");
            if (name.isEmpty() || baseUrl.isEmpty()) {
                continue;
            }
            list.add(new Provider(
                    ID_CUSTOM_PREFIX + Provider.slug(name),
                    name,
                    Provider.WIRE_OPENAI,
                    Provider.chatCompletionsUrl(baseUrl),
                    trim(JSONCompat.optString(e, "apiKey", "")),
                    JSONCompat.optString(e, "model", ""),
                    null,
                    Provider.modelsUrl(baseUrl),
                    null));
        }
        return list;
    }

    /** Raw stored entries, for the settings screen to edit. Never null. */
    public static List<JSONObject> customEntries(SharedPreferences prefs) {
        return parseEntries(prefs.getString(PREF_CUSTOM_PROVIDERS, ""));
    }

    /** Entries held in a stored blob. Never null; a corrupt blob yields none. */
    public static List<JSONObject> parseEntries(String raw) {
        List<JSONObject> list = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return list;
        }
        try {
            JSONArray items = JSONCompat.optJSONArray(new JSONObject(raw), FIELD_ITEMS);
            if (items == null) {
                return list;
            }
            for (int i = 0; i < JSONCompat.size(items); i++) {
                JSONObject e = JSONCompat.optJSONObject(items, i);
                if (e != null) {
                    list.add(e);
                }
            }
        } catch (Exception ignored) {
            // A corrupt blob must not take the built-in providers down with it.
        }
        return list;
    }

    public static void saveCustomEntries(SharedPreferences prefs, List<JSONObject> entries) {
        JSONArray items = new JSONArray();
        for (JSONObject e : entries) {
            JSONCompat.put(items, e);
        }
        JSONObject payload = new JSONObject();
        payload.put(FIELD_ITEMS, items);
        prefs.edit().putString(PREF_CUSTOM_PROVIDERS, payload.toString()).apply();
    }

    /**
     * The same entry list with every API key blanked.
     *
     * <p>Used when writing a settings preset: a preset is meant to be handed to
     * someone else, and a user-defined endpoint keeps its key in the same blob
     * as its name and URL. Returns an empty list blob for unreadable input.
     */
    public static String withoutKeys(String raw) {
        JSONArray items = new JSONArray();
        for (JSONObject e : parseEntries(raw)) {
            JSONCompat.put(items, newCustomEntry(
                    JSONCompat.optString(e, "name", ""),
                    JSONCompat.optString(e, "baseUrl", ""),
                    "",
                    JSONCompat.optString(e, "model", "")));
        }
        JSONObject payload = new JSONObject();
        payload.put(FIELD_ITEMS, items);
        return payload.toString();
    }

    /** Builds one stored entry from the settings screen's four fields. */
    public static JSONObject newCustomEntry(String name, String baseUrl, String apiKey, String model) {
        JSONObject e = new JSONObject();
        e.put("name", name);
        e.put("baseUrl", baseUrl);
        e.put("apiKey", apiKey);
        e.put("model", model);
        return e;
    }

    private static String trim(String key) {
        return key == null ? "" : key.trim();
    }
}
