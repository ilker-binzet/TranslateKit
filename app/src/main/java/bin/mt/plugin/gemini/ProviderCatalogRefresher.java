package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.List;

/**
 * Shared logic for the three AI provider preference screens (Gemini, OpenAI,
 * Claude). Centralises:
 * <ul>
 *   <li>Rendering a List preference from a merged (seed + cached) model list</li>
 *   <li>Triggering an auto-refresh on first open (silent, background)</li>
 *   <li>Producing a "Last refreshed" hint for the UI</li>
 *   <li>Resolving the effective selected model: custom override &gt; list selection &gt; default</li>
 * </ul>
 *
 * <p>Each provider's {@code onBuild()} should call
 * {@link #bindModelList(SharedPreferences, Builder, List, Provider, String, String, String)}
 * and {@link #scheduleAutoRefresh(SharedPreferences, String, String, Provider)}.</p>
 */
public final class ProviderCatalogRefresher {

    private ProviderCatalogRefresher() {}

    /**
     * Effective selected model: custom override (if non-empty) wins, otherwise
     * the persisted list selection (or the supplied default).
     */
    public static String resolveSelectedModel(SharedPreferences prefs,
                                             String modelPrefKey,
                                             String customPrefKey,
                                             String defaultValue) {
        String custom = prefs.getString(customPrefKey, "");
        if (!TextUtils.isEmpty(custom)) return custom.trim();
        String persisted = prefs.getString(modelPrefKey, "");
        if (!TextUtils.isEmpty(persisted)) return persisted;
        return defaultValue;
    }

    /**
     * Convenience overload that derives the custom-key from the model-key
     * (the convention used throughout the plugin). Use this from the engine
     * code so the resolution rule stays in one place.
     */
    public static String resolveSelectedModel(SharedPreferences prefs,
                                             String modelPrefKey,
                                             String defaultValue) {
        return resolveSelectedModel(prefs, modelPrefKey, customPrefKeyFor(modelPrefKey), defaultValue);
    }

    /**
     * Compose the final list to show in the {@code addList} preference, by
     * merging the live API cache on top of the curated seed. Falls back to
     * seed-only when cache is empty.
     */
    public static List<ModelCatalogManager.ModelInfo> composeList(
            SharedPreferences prefs,
            String cacheKey,
            List<ModelCatalogManager.ModelInfo> seed) {
        if (prefs.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false)) {
            return seed;
        }
        List<ModelCatalogManager.ModelInfo> cached =
                ModelCatalogManager.loadModelCache(prefs, cacheKey);
        return ModelCatalogManager.mergeWithSeed(cached, seed);
    }

    /**
     * Persist the user's custom model override (or clear it when blank).
     */
    public static void setCustomModel(SharedPreferences prefs,
                                      String customPrefKey,
                                      String value) {
        SharedPreferences.Editor e = prefs.edit();
        if (TextUtils.isEmpty(value) || value.trim().isEmpty()) {
            e.remove(customPrefKey);
        } else {
            e.putString(customPrefKey, value.trim());
        }
        e.apply();
    }

    /**
     * Trigger a silent background refresh if the cache is empty or stale.
     * No-op when debug cache bypass is on, when no API key is configured, or
     * when the cache is still fresh.
     */
    public static void scheduleAutoRefresh(SharedPreferences prefs,
                                          String cacheKey,
                                          String apiKey,
                                          ModelCatalogManager.Provider provider) {
        ModelCatalogManager.refreshIfStale(
                prefs, cacheKey, apiKey,
                GeminiConstants.MODEL_CACHE_TTL_MS,
                provider,
                null
        );
    }

    /**
     * The custom-model override preference key for a given model preference key.
     *   "gemini_model_name" -> "gemini_custom_model"
     *   "openai_model_name" -> "openai_custom_model"
     *   "claude_model_name" -> "claude_custom_model"
     */
    public static String customPrefKeyFor(String modelPrefKey) {
        int dot = modelPrefKey.lastIndexOf('_');
        String prefix = dot > 0 ? modelPrefKey.substring(0, dot) : modelPrefKey;
        return prefix + GeminiConstants.PREF_CUSTOM_MODEL;
    }
}
