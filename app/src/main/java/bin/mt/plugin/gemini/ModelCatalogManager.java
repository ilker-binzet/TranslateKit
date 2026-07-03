package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import bin.mt.json.JSONArray;
import bin.mt.json.JSONObject;
import bin.mt.plugin.common.HttpUtils;
import bin.mt.plugin.common.JSONCompat;

/**
 * Centralized helper to fetch and cache provider model catalogs.
 */
public class ModelCatalogManager {

    public static class ModelInfo {
        public final String id;
        public final String displayName;
        public final String detail;
        public final boolean recommended;
        public final int priority;

        public ModelInfo(String id, String displayName, String detail, boolean recommended, int priority) {
            this.id = id;
            this.displayName = displayName;
            this.detail = detail;
            this.recommended = recommended;
            this.priority = priority;
        }
    }

    public static class CacheDiagnostics {
        public final String key;
        public final int modelCount;
        public final long fetchedAt;
        public final boolean hasData;
        public final boolean expired;
        public final long ageMs;

        CacheDiagnostics(String key, int modelCount, long fetchedAt, boolean hasData, boolean expired, long ageMs) {
            this.key = key;
            this.modelCount = modelCount;
            this.fetchedAt = fetchedAt;
            this.hasData = hasData;
            this.expired = expired;
            this.ageMs = ageMs;
        }
    }

    private static final String FIELD_FETCHED_AT = "fetched_at";
    private static final String FIELD_MODELS = "models";

    private ModelCatalogManager() {}

    // ==================== Default seeds (no network) ====================

    /** Build a ModelInfo from a row of the seed array. */
    private static ModelInfo seedRow(String[] row) {
        return new ModelInfo(
                row[0],                          // id
                row[1],                          // displayName
                row[2],                          // detail
                "true".equalsIgnoreCase(row[3]), // recommended
                parseIntSafe(row[4], 0)          // priority
        );
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }

    /** Curated Gemini list — used when no cache or fetch fails. */
    public static List<ModelInfo> getDefaultSeedGemini() {
        List<ModelInfo> out = new ArrayList<>(GeminiConstants.GEMINI_SEED.length);
        for (String[] row : GeminiConstants.GEMINI_SEED) out.add(seedRow(row));
        return out;
    }

    /** Curated OpenAI list — used when no cache or fetch fails. */
    public static List<ModelInfo> getDefaultSeedOpenAi() {
        List<ModelInfo> out = new ArrayList<>(GeminiConstants.OPENAI_SEED.length);
        for (String[] row : GeminiConstants.OPENAI_SEED) out.add(seedRow(row));
        return out;
    }

    /** Curated Claude list — used when no cache or fetch fails. */
    public static List<ModelInfo> getDefaultSeedClaude() {
        List<ModelInfo> out = new ArrayList<>(GeminiConstants.CLAUDE_SEED.length);
        for (String[] row : GeminiConstants.CLAUDE_SEED) out.add(seedRow(row));
        return out;
    }

    /**
     * Merge live-fetched models on top of the seed. Live list always wins on
     * id collision. New models from the API appear; old/missing models
     * disappear. Seed entries that the API has confirmed also exist
     * retain their curated priority; pure-API entries use the assigned
     * priority from the fetcher.
     */
    public static List<ModelInfo> mergeWithSeed(List<ModelInfo> live, List<ModelInfo> seed) {
        if (live == null || live.isEmpty()) {
            return seed == null ? Collections.<ModelInfo>emptyList() : seed;
        }
        if (seed == null || seed.isEmpty()) {
            return live;
        }
        Map<String, ModelInfo> byId = new LinkedHashMap<>();
        for (ModelInfo m : live) byId.put(m.id, m);
        for (ModelInfo m : seed) {
            if (!byId.containsKey(m.id)) byId.put(m.id, m);
        }
        List<ModelInfo> out = new ArrayList<>(byId.values());
        sortModels(out);
        return out;
    }

    /**
     * Read the cached fetched_at timestamp. Returns 0 if never cached.
     * (Already in milliseconds since epoch.)
     */
    public static long getLastRefreshedAt(SharedPreferences prefs, String cacheKey) {
        if (prefs == null || cacheKey == null) return 0;
        String raw = prefs.getString(cacheKey, null);
        if (TextUtils.isEmpty(raw)) return 0;
        try {
            JSONObject payload = new JSONObject(raw);
            return JSONCompat.optLong(payload, FIELD_FETCHED_AT, 0);
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Human-readable "Last refreshed: ..." text for the UI.
     * Returns "Never refreshed" when no cache exists.
     */
    public static String formatLastRefreshed(SharedPreferences prefs, String cacheKey) {
        long fetchedAt = getLastRefreshedAt(prefs, cacheKey);
        if (fetchedAt <= 0) return "Never refreshed (using default list)";
        long ageMs = System.currentTimeMillis() - fetchedAt;
        if (ageMs < 0) return "Last refreshed: just now";
        long minutes = ageMs / 60_000;
        if (minutes < 1) return "Last refreshed: just now";
        if (minutes < 60) return "Last refreshed: " + minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return "Last refreshed: " + hours + " h ago";
        long days = hours / 24;
        return "Last refreshed: " + days + " d ago";
    }

    /**
     * Check if cache is missing or older than the TTL.
     */
    public static boolean isCacheStale(SharedPreferences prefs, String cacheKey, long ttlMs) {
        if (prefs == null || cacheKey == null) return true;
        long fetchedAt = getLastRefreshedAt(prefs, cacheKey);
        if (fetchedAt <= 0) return true;
        if (ttlMs <= 0) return false; // disabled TTL means "always fresh"
        return (System.currentTimeMillis() - fetchedAt) > ttlMs;
    }

    /**
     * Auto-refresh the catalog if the cache is stale (or empty) AND an API key
     * is available. Runs in a background thread; the result is saved to the
     * cache and reported via {@code callback}. Safe to call repeatedly —
     * only one refresh happens at a time per cache key.
     *
     * Use this in onBuild() of provider preferences so the list picks up new
     * models without requiring the user to click "Refresh".
     */
    public static void refreshIfStale(SharedPreferences prefs,
                                      String cacheKey,
                                      String apiKey,
                                      long ttlMs,
                                      Provider provider,
                                      RefreshCallback callback) {
        if (prefs == null || cacheKey == null) return;
        // Skip when debug cache bypass is active.
        if (prefs.getBoolean(GeminiConstants.PREF_DEBUG_DISABLE_MODEL_CACHE, false)) return;
        // Skip when no API key — seed list will be used.
        if (TextUtils.isEmpty(apiKey)) return;
        // Skip when cache is fresh and non-empty.
        if (!isCacheStale(prefs, cacheKey, ttlMs)) return;
        // Skip when cache exists and has models (only refresh when truly empty).
        List<ModelInfo> existing = loadModelCache(prefs, cacheKey, ttlMs);
        if (existing != null && !existing.isEmpty() && !isCacheStale(prefs, cacheKey, ttlMs)) return;
        // Trigger the fetch.
        triggerRefresh(prefs, cacheKey, apiKey, provider, callback);
    }

    /**
     * Force-refresh the catalog regardless of cache state. Same threading
     * contract as {@link #refreshIfStale}.
     */
    public static void forceRefresh(SharedPreferences prefs,
                                    String cacheKey,
                                    String apiKey,
                                    Provider provider,
                                    RefreshCallback callback) {
        triggerRefresh(prefs, cacheKey, apiKey, provider, callback);
    }

    private static void triggerRefresh(SharedPreferences prefs,
                                       String cacheKey,
                                       String apiKey,
                                       Provider provider,
                                       RefreshCallback callback) {
        new Thread(() -> {
            try {
                List<ModelInfo> models;
                switch (provider) {
                    case GEMINI: models = fetchGeminiModels(apiKey); break;
                    case OPENAI: models = fetchOpenAiModels(apiKey); break;
                    case CLAUDE: models = fetchClaudeModels(apiKey); break;
                    default: models = Collections.emptyList();
                }
                saveModelCache(prefs, cacheKey, models);
                if (callback != null) callback.onResult(models, null);
            } catch (Throwable e) {
                // Catch Throwable, not Exception: an Error (e.g. NoSuchMethodError
                // from an SDK/host API mismatch) escaping this thread would crash
                // the whole MT Manager process. Report it as a failed refresh.
                if (callback != null) {
                    callback.onResult(null, e instanceof Exception
                            ? (Exception) e
                            : new RuntimeException(e));
                }
            }
        }, "TranslateKit-ModelRefresh").start();
    }

    /** Provider identifier for refresh operations. */
    public enum Provider { GEMINI, OPENAI, CLAUDE }

    /** Result of an auto-refresh attempt. */
    public interface RefreshCallback {
        void onResult(List<ModelInfo> models, Exception error);
    }

    public static void saveModelCache(SharedPreferences prefs, String key, List<ModelInfo> models) {
        if (prefs == null || key == null) {
            return;
        }
        JSONObject payload = new JSONObject();
        JSONArray data = new JSONArray();
        long now = System.currentTimeMillis();
        try {
            payload.put(FIELD_FETCHED_AT, now);
            for (ModelInfo info : models) {
                JSONObject obj = new JSONObject();
                obj.put("id", info.id);
                obj.put("name", info.displayName);
                obj.put("detail", info.detail);
                obj.put("recommended", info.recommended);
                obj.put("priority", info.priority);
                JSONCompat.put(data, obj);
            }
            payload.put(FIELD_MODELS, data);
            prefs.edit().putString(key, payload.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<ModelInfo> loadModelCache(SharedPreferences prefs, String key) {
        return loadModelCache(prefs, key, GeminiConstants.MODEL_CACHE_TTL_MS);
    }

    public static List<ModelInfo> loadModelCache(SharedPreferences prefs, String key, long ttlMs) {
        if (prefs == null || key == null) {
            return Collections.emptyList();
        }
        String raw = prefs.getString(key, null);
        if (TextUtils.isEmpty(raw)) {
            return Collections.emptyList();
        }
        try {
            JSONObject payload = new JSONObject(raw);
            long fetchedAt = JSONCompat.optLong(payload, FIELD_FETCHED_AT, 0);
            if (ttlMs > 0 && (System.currentTimeMillis() - fetchedAt) > ttlMs) {
                return Collections.emptyList();
            }
            JSONArray data = JSONCompat.optJSONArray(payload, FIELD_MODELS);
            if (data == null || JSONCompat.size(data) == 0) {
                return Collections.emptyList();
            }
            List<ModelInfo> result = new ArrayList<>();
            for (int i = 0; i < JSONCompat.size(data); i++) {
                JSONObject obj = JSONCompat.optJSONObject(data, i);
                if (obj == null) continue;
                result.add(new ModelInfo(
                        JSONCompat.optString(obj, "id", ""),
                        JSONCompat.optString(obj, "name", ""),
                        JSONCompat.optString(obj, "detail", ""),
                        JSONCompat.optBoolean(obj, "recommended", false),
                        JSONCompat.optInt(obj, "priority", 0)
                ));
            }
            sortModels(result);
            return result;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public static CacheDiagnostics inspectCache(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) {
            return new CacheDiagnostics(key, 0, 0, false, false, -1);
        }
        String raw = prefs.getString(key, null);
        if (TextUtils.isEmpty(raw)) {
            return new CacheDiagnostics(key, 0, 0, false, false, -1);
        }
        try {
            JSONObject payload = new JSONObject(raw);
            long fetchedAt = JSONCompat.optLong(payload, FIELD_FETCHED_AT, 0);
            JSONArray data = JSONCompat.optJSONArray(payload, FIELD_MODELS);
            int count = data != null ? JSONCompat.size(data) : 0;
            boolean hasData = count > 0;
            long ageMs = fetchedAt > 0 ? System.currentTimeMillis() - fetchedAt : -1;
            boolean expired = fetchedAt > 0 && ageMs > GeminiConstants.MODEL_CACHE_TTL_MS;
            return new CacheDiagnostics(key, count, fetchedAt, hasData, expired, ageMs);
        } catch (Exception ignored) {
            return new CacheDiagnostics(key, 0, 0, false, false, -1);
        }
    }

    public static void clearModelCache(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) {
            return;
        }
        prefs.edit().remove(key).apply();
    }

    public static List<ModelInfo> fetchOpenAiModels(String apiKey) throws IOException {
        if (TextUtils.isEmpty(apiKey)) {
            throw new IOException("OpenAI API key required to fetch models");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        JSONObject response = HttpUtils.getJson("https://api.openai.com/v1/models", headers);
        JSONArray data = JSONCompat.optJSONArray(response, "data");
        if (data == null) {
            return Collections.emptyList();
        }
        List<ModelInfo> models = new ArrayList<>();
        for (int i = 0; i < JSONCompat.size(data); i++) {
            JSONObject entry = JSONCompat.optJSONObject(data, i);
            if (entry == null) continue;
            String id = JSONCompat.optString(entry, "id", "");
            if (!isOpenAiChatModel(id)) {
                continue;
            }
            models.add(new ModelInfo(
                    id,
                    formatOpenAiName(id),
                    JSONCompat.optString(entry, "owned_by", "OpenAI"),
                    isRecommendedOpenAiModel(id),
                    priorityForOpenAi(id)
            ));
        }
        sortModels(models);
        return models;
    }

    public static List<ModelInfo> fetchClaudeModels(String apiKey) throws IOException {
        if (TextUtils.isEmpty(apiKey)) {
            throw new IOException("Claude API key required to fetch models");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", GeminiConstants.CLAUDE_API_VERSION);
        JSONObject response = HttpUtils.getJson(GeminiConstants.CLAUDE_MODELS_ENDPOINT, headers);
        JSONArray data = JSONCompat.optJSONArray(response, "data");
        if (data == null) {
            return Collections.emptyList();
        }
        List<ModelInfo> models = new ArrayList<>();
        for (int i = 0; i < JSONCompat.size(data); i++) {
            JSONObject entry = JSONCompat.optJSONObject(data, i);
            if (entry == null) continue;
            String id = JSONCompat.optString(entry, "id", "");
            if (!id.startsWith("claude")) {
                continue;
            }
            models.add(new ModelInfo(
                    id,
                    formatClaudeName(id),
                    JSONCompat.optString(entry, "display_name", "Anthropic Claude"),
                    isRecommendedClaudeModel(id),
                    priorityForClaude(id)
            ));
        }
        sortModels(models);
        return models;
    }

    public static List<ModelInfo> fetchGeminiModels(String apiKey) throws IOException {
        String url = GeminiConstants.API_BASE_URL;
        if (!TextUtils.isEmpty(apiKey)) {
            url = url + "?key=" + urlEncode(apiKey);
        }
        JSONObject response = HttpUtils.getJson(url);
        JSONArray modelsArray = JSONCompat.optJSONArray(response, "models");
        if (modelsArray == null) {
            // Some responses nest under "data"
            modelsArray = JSONCompat.optJSONArray(response, "data");
        }
        if (modelsArray == null) {
            return Collections.emptyList();
        }
        List<ModelInfo> models = new ArrayList<>();
        for (int i = 0; i < JSONCompat.size(modelsArray); i++) {
            JSONObject entry = JSONCompat.optJSONObject(modelsArray, i);
            if (entry == null) continue;
                String fullName = JSONCompat.optString(entry, "name", "");
                String modelId = normalizeGeminiId(fullName);
                if (!isGeminiModelEligible(modelId)) {
                continue;
            }
            models.add(new ModelInfo(
                    modelId,
                    JSONCompat.optString(entry, "displayName", formatGeminiName(modelId)),
                    JSONCompat.optString(entry, "description", "Google Gemini"),
                    isRecommendedGeminiModel(modelId),
                    priorityForGemini(modelId)
            ));
        }
        sortModels(models);
        return models;
    }

    public static String selectBestModel(List<ModelInfo> models, String defaultValue) {
        if (models == null || models.isEmpty()) {
            return defaultValue;
        }
        for (ModelInfo info : models) {
            if (info.recommended) {
                return info.id;
            }
        }
        return models.get(0).id;
    }

    private static void sortModels(List<ModelInfo> models) {
        Collections.sort(models, Comparator
                .comparingInt((ModelInfo m) -> -m.priority)
                .thenComparing(m -> m.displayName));
    }

    private static boolean isOpenAiChatModel(String id) {
        if (TextUtils.isEmpty(id)) {
            return false;
        }
        String lower = id.toLowerCase(Locale.US);
        // Exclude non-chat modalities.
        if (lower.contains("audio") || lower.contains("embedding") || lower.contains("realtime")
                || lower.contains("tts") || lower.contains("whisper") || lower.contains("dall-e")
                || lower.contains("image") || lower.contains("vision") && !lower.contains("gpt-4o")) {
            return false;
        }
        // Future-proof: any gpt-* chat model and any o*-reasoning model.
        if (lower.startsWith("gpt-") || lower.matches("o\\d+(-mini)?")) {
            return true;
        }
        return false;
    }

    private static boolean isRecommendedOpenAiModel(String id) {
        if (id == null) return false;
        // Recommended = cheapest member of the newest production family.
        // Currently: gpt-4.1-mini.  Will auto-pick e.g. gpt-5.3-mini when
        // it appears, because the family check is regex-based.
        return id.matches("gpt-\\d+(\\.\\d+)?-mini") || id.startsWith("gpt-4.1") || "gpt-4o".equals(id);
    }

    private static int priorityForOpenAi(String id) {
        if (id == null) {
            return 0;
        }
        String lower = id.toLowerCase(Locale.US);
        // gpt-5.x family (newest) — higher priority as the major grows.
        if (lower.startsWith("gpt-5")) {
            if (id.contains("mini")) return 118;
            return 115; // gpt-5, gpt-5.1, gpt-5.2, gpt-5.3 — all share this band
        }
        if (id.startsWith("gpt-4.1-mini")) return 120;
        if (id.startsWith("gpt-4.1")) return 110;
        if (id.startsWith("gpt-4o-mini")) return 88;
        if (id.startsWith("gpt-4o")) return 90;
        if (id.startsWith("o4-mini")) return 85;
        if (id.startsWith("o3")) return 80;
        if (id.startsWith("gpt-4-turbo")) return 70;
        if (id.startsWith("gpt-3.5")) return 40;
        // gpt-6.x or other future — keep discoverable.
        if (lower.startsWith("gpt-6")) return 130;
        if (lower.startsWith("o5") || lower.startsWith("o6")) return 95;
        return 10;
    }

    private static String formatOpenAiName(String id) {
        if (TextUtils.isEmpty(id)) {
            return "OpenAI Model";
        }
        String lower = id.toLowerCase(Locale.US);
        // o-series (o3, o3-mini, o4-mini, future o5, o5-mini, …)
        if (lower.matches("o\\d+(-mini)?")) {
            // "o4-mini" -> "o4-mini", "o3" -> "o3"
            return id;
        }
        // GPT-5.x, GPT-4.1, GPT-4o, etc.  Display "GPT " + readable version.
        if (lower.startsWith("gpt-")) {
            String tail = id.substring(4); // "4.1-mini", "4o", "5.2"
            // Convert dashes/dots to spaces and title-case the alphabetic bits.
            StringBuilder sb = new StringBuilder("GPT ");
            for (String token : tail.split("[.\\-]")) {
                if (token.isEmpty()) continue;
                if (sb.length() > 4) sb.append(' ');
                // 4o, 5x, etc. keep their lowercase form; letters otherwise title-cased.
                if (token.matches("[a-z]+")) sb.append(token);
                else sb.append(token);
            }
            return sb.toString();
        }
        // Generic gpt-* legacy
        if (lower.startsWith("gpt-4-turbo")) return "GPT-4 Turbo";
        if (lower.startsWith("gpt-3.5")) return "GPT-3.5 Turbo";
        // Unknown new model — surface the raw id rather than show "GPT".
        return id;
    }

    private static boolean isRecommendedClaudeModel(String id) {
        if (id == null) return false;
        // Recommended = latest Sonnet of the latest major family.
        // Future-proof: any "claude-sonnet-X-Y-latest" (where X is the newest major).
        // Heuristic: pick the most recent major that contains "sonnet" + "latest" suffix.
        return id.contains("sonnet-4-5") || id.contains("sonnet-5");
    }

    private static int priorityForClaude(String id) {
        if (id == null) return 0;
        // 4.5 family (Sonnet 4.5, Haiku 4.5)
        if (id.contains("sonnet-4-5")) return 130;
        if (id.contains("haiku-4-5")) return 120;
        // 4.6 family — newest
        if (id.contains("opus-4-6") || id.contains("sonnet-4-6") || id.contains("haiku-4-6")) return 125;
        // 4.x Opus / Sonnet (generic)
        if (id.contains("opus-4")) return 110;
        if (id.contains("sonnet-4")) return 100;
        // 3.x family
        if (id.startsWith("claude-3-5-sonnet")) return 90;
        if (id.startsWith("claude-3-5-haiku")) return 80;
        if (id.startsWith("claude-3-opus")) return 70;
        if (id.startsWith("claude-3-sonnet")) return 60;
        if (id.startsWith("claude-3-haiku")) return 50;
        // 5.x family (future) — between 4.5 and 4.x
        if (id.contains("opus-5") || id.contains("sonnet-5") || id.contains("haiku-5")) return 115;
        return 10;
    }

    private static String formatClaudeName(String id) {
        if (TextUtils.isEmpty(id)) return "Claude Model";
        String lower = id.toLowerCase(Locale.US);
        // 4.5 family
        if (id.contains("sonnet-4-5")) return "Claude Sonnet 4.5";
        if (id.contains("haiku-4-5")) return "Claude Haiku 4.5";
        // 4.6 family
        if (id.contains("opus-4-6")) return "Claude Opus 4.6";
        if (id.contains("sonnet-4-6")) return "Claude Sonnet 4.6";
        if (id.contains("haiku-4-6")) return "Claude Haiku 4.6";
        // 4.x generic
        if (id.contains("opus-4")) return "Claude Opus 4";
        if (id.contains("sonnet-4")) return "Claude Sonnet 4";
        if (id.contains("haiku-4")) return "Claude Haiku 4";
        // 3.x legacy
        if (id.startsWith("claude-3-5-sonnet")) return "Claude 3.5 Sonnet";
        if (id.startsWith("claude-3-5-haiku")) return "Claude 3.5 Haiku";
        if (id.startsWith("claude-3-opus")) return "Claude 3 Opus";
        if (id.startsWith("claude-3-sonnet")) return "Claude 3 Sonnet";
        if (id.startsWith("claude-3-haiku")) return "Claude 3 Haiku";
        // 5.x future
        if (lower.contains("opus-5")) return "Claude Opus 5";
        if (lower.contains("sonnet-5")) return "Claude Sonnet 5";
        if (lower.contains("haiku-5")) return "Claude Haiku 5";
        // Unknown new model — surface raw id.
        return id;
    }

    private static boolean isGeminiModelEligible(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        if (!lower.startsWith("gemini")) {
            return false;
        }
        // Exclude experimental previews only; everything else (flash/pro/nano/lite
        // across all versions) is eligible. Future versions auto-qualify.
        if (lower.contains("-exp")) {
            return false;
        }
        return lower.contains("flash") || lower.contains("pro") || lower.contains("nano");
    }

    private static boolean isRecommendedGeminiModel(String name) {
        // Recommended = the stable 2.x-flash tier that the API confirmed as GA.
        // When 3-flash goes stable, the same regex matches the new model.
        if (name == null) return false;
        return name.matches("gemini-\\d+(\\.\\d+)?-flash") && !name.contains("lite");
    }

    private static int priorityForGemini(String name) {
        if (name == null) return 0;
        String lower = name.toLowerCase(Locale.US);
        // 2.5-flash (stable) is the default — top of list
        if (lower.contains("2.5-flash") && !lower.contains("lite")) return 130;
        if (lower.contains("2.5-flash-lite")) return 120;
        if (lower.contains("2.5-pro")) return 110;
        // 3.x preview family
        if (lower.contains("3") && lower.contains("flash")) return 115;
        if (lower.contains("3") && lower.contains("pro")) return 108;
        if (lower.contains("3")) return 100;
        // 2.0 legacy
        if (lower.contains("2.0-flash")) return 80;
        // Future 4.x family
        if (lower.contains("4")) return 140;
        return 10;
    }

    private static String formatGeminiName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "Gemini Model";
        }
        // "gemini-2.5-flash" -> "Gemini 2.5 Flash", "gemini-3-pro-preview" -> "Gemini 3 Pro (Preview)"
        String[] parts = name.split("-");
        StringBuilder sb = new StringBuilder("Gemini");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(' ');
            // Version tokens (digits, dots) stay as-is
            if (p.matches("\\d+(\\.\\d+)?")) sb.append(p);
            // Tier tokens
            else if (p.equalsIgnoreCase("flash")) sb.append("Flash");
            else if (p.equalsIgnoreCase("pro")) sb.append("Pro");
            else if (p.equalsIgnoreCase("lite")) sb.append("Flash-Lite");
            // Preview / Experimental / Nano suffixes
            else if (p.equalsIgnoreCase("preview")) sb.append("(Preview)");
            else if (p.equalsIgnoreCase("exp")) sb.append("(Experimental)");
            else if (p.equalsIgnoreCase("nano")) sb.append("Nano");
            else sb.append(p);
        }
        return sb.toString();
    }

    private static String normalizeGeminiId(String name) {
        if (TextUtils.isEmpty(name)) {
            return name;
        }
        if (name.startsWith("models/")) {
            return name.substring("models/".length());
        }
        return name;
    }

    private static String urlEncode(String value) throws IOException {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IOException("Failed to encode value", e);
        }
    }

    public static Map<String, String> buildLabelMap(List<ModelInfo> models) {
        Map<String, String> map = new HashMap<>();
        if (models == null) {
            return map;
        }
        for (ModelInfo info : models) {
            map.put(info.id, info.displayName);
        }
        return map;
    }
}
