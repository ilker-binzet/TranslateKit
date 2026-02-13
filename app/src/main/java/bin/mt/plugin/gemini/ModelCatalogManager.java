package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
                data.put(obj);
            }
            payload.put(FIELD_MODELS, data);
            prefs.edit().putString(key, payload.toString()).apply();
        } catch (JSONException ignored) {
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
            long fetchedAt = payload.optLong(FIELD_FETCHED_AT, 0);
            if (ttlMs > 0 && (System.currentTimeMillis() - fetchedAt) > ttlMs) {
                return Collections.emptyList();
            }
            JSONArray data = payload.optJSONArray(FIELD_MODELS);
            if (data == null || data.length() == 0) {
                return Collections.emptyList();
            }
            List<ModelInfo> result = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject obj = data.optJSONObject(i);
                if (obj == null) continue;
                result.add(new ModelInfo(
                        obj.optString("id"),
                        obj.optString("name"),
                        obj.optString("detail"),
                        obj.optBoolean("recommended", false),
                        obj.optInt("priority", 0)
                ));
            }
            sortModels(result);
            return result;
        } catch (JSONException ignored) {
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
            long fetchedAt = payload.optLong(FIELD_FETCHED_AT, 0);
            JSONArray data = payload.optJSONArray(FIELD_MODELS);
            int count = data != null ? data.length() : 0;
            boolean hasData = count > 0;
            long ageMs = fetchedAt > 0 ? System.currentTimeMillis() - fetchedAt : -1;
            boolean expired = fetchedAt > 0 && ageMs > GeminiConstants.MODEL_CACHE_TTL_MS;
            return new CacheDiagnostics(key, count, fetchedAt, hasData, expired, ageMs);
        } catch (JSONException ignored) {
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
        GeminiHttpUtils.Request request = GeminiHttpUtils.get("https://api.openai.com/v1/models");
        request.header("Authorization", "Bearer " + apiKey);
        request.setTimeout(GeminiConstants.DEFAULT_TIMEOUT);
        JSONObject response = request.executeToJson();
        JSONArray data = response.optJSONArray("data");
        if (data == null) {
            return Collections.emptyList();
        }
        List<ModelInfo> models = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry == null) continue;
            String id = entry.optString("id", "");
            if (!isOpenAiChatModel(id)) {
                continue;
            }
            models.add(new ModelInfo(
                    id,
                    formatOpenAiName(id),
                    entry.optString("owned_by", "OpenAI"),
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
        GeminiHttpUtils.Request request = GeminiHttpUtils.get(GeminiConstants.CLAUDE_MODELS_ENDPOINT);
        request.header("x-api-key", apiKey);
        request.header("anthropic-version", GeminiConstants.CLAUDE_API_VERSION);
        request.setTimeout(GeminiConstants.DEFAULT_TIMEOUT);
        JSONObject response = request.executeToJson();
        JSONArray data = response.optJSONArray("data");
        if (data == null) {
            return Collections.emptyList();
        }
        List<ModelInfo> models = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry == null) continue;
            String id = entry.optString("id", "");
            if (!id.startsWith("claude")) {
                continue;
            }
            models.add(new ModelInfo(
                    id,
                    formatClaudeName(id),
                    entry.optString("display_name", "Anthropic Claude"),
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
        GeminiHttpUtils.Request request = GeminiHttpUtils.get(url);
        request.setTimeout(GeminiConstants.DEFAULT_TIMEOUT);
        JSONObject response = request.executeToJson();
        JSONArray modelsArray = response.optJSONArray("models");
        if (modelsArray == null) {
            // Some responses nest under "data"
            modelsArray = response.optJSONArray("data");
        }
        if (modelsArray == null) {
            return Collections.emptyList();
        }
        List<ModelInfo> models = new ArrayList<>();
        for (int i = 0; i < modelsArray.length(); i++) {
            JSONObject entry = modelsArray.optJSONObject(i);
            if (entry == null) continue;
                String fullName = entry.optString("name", "");
                String modelId = normalizeGeminiId(fullName);
                if (!isGeminiModelEligible(modelId)) {
                continue;
            }
            models.add(new ModelInfo(
                    modelId,
                    entry.optString("displayName", formatGeminiName(modelId)),
                    entry.optString("description", "Google Gemini"),
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
        if (lower.contains("audio") || lower.contains("embedding") || lower.contains("embedding")) {
            return false;
        }
        if (lower.startsWith("gpt-4") || lower.startsWith("gpt-3.5") || lower.startsWith("o3") || lower.startsWith("o4")) {
            return true;
        }
        return lower.startsWith("gpt-5") || lower.startsWith("gpt-4o");
    }

    private static boolean isRecommendedOpenAiModel(String id) {
        return id.startsWith("gpt-4.1-mini") || id.startsWith("gpt-4.1") || "gpt-4o".equals(id);
    }

    private static int priorityForOpenAi(String id) {
        if (id == null) {
            return 0;
        }
        if (id.startsWith("gpt-4.1-mini")) return 120;
        if (id.startsWith("gpt-4.1")) return 110;
        if (id.startsWith("gpt-5")) return 100;
        if (id.startsWith("gpt-4o")) return 90;
        if (id.startsWith("o4-mini")) return 85;
        if (id.startsWith("o3")) return 80;
        if (id.startsWith("gpt-4-turbo")) return 70;
        if (id.startsWith("gpt-3.5")) return 40;
        return 10;
    }

    private static String formatOpenAiName(String id) {
        if (TextUtils.isEmpty(id)) {
            return "OpenAI Model";
        }
        if (id.startsWith("gpt-4.1-mini")) return "GPT-4.1 Mini (Recommended)";
        if (id.startsWith("gpt-4.1")) return "GPT-4.1";
        if ("gpt-4o".equals(id)) return "GPT-4o";
        if (id.startsWith("gpt-4o-mini")) return "GPT-4o Mini";
        if (id.startsWith("gpt-5")) return "GPT-5";
        if (id.startsWith("o4-mini")) return "o4-mini Reasoning";
        if (id.startsWith("o3")) return "o3 Reasoning";
        if (id.startsWith("gpt-4-turbo")) return "GPT-4 Turbo";
        if (id.startsWith("gpt-3.5")) return "GPT-3.5 Turbo";
        String[] parts = id.split("-");
        if (parts.length > 0) {
            return parts[0].toUpperCase(Locale.US);
        }
        return id;
    }

    private static boolean isRecommendedClaudeModel(String id) {
        return id != null && (id.contains("sonnet-4") || id.contains("haiku-4"));
    }

    private static int priorityForClaude(String id) {
        if (id == null) return 0;
        if (id.contains("sonnet-4-5")) return 130;
        if (id.contains("haiku-4-5")) return 120;
        if (id.contains("opus-4")) return 110;
        if (id.contains("sonnet-4")) return 100;
        if (id.startsWith("claude-3-5-sonnet")) return 90;
        if (id.startsWith("claude-3-5-haiku")) return 80;
        if (id.startsWith("claude-3-opus")) return 70;
        if (id.startsWith("claude-3-sonnet")) return 60;
        if (id.startsWith("claude-3-haiku")) return 50;
        return 10;
    }

    private static String formatClaudeName(String id) {
        if (TextUtils.isEmpty(id)) return "Claude Model";
        if (id.contains("sonnet-4-5")) return "Claude Sonnet 4.5 (Recommended)";
        if (id.contains("haiku-4-5")) return "Claude Haiku 4.5";
        if (id.contains("opus-4")) return "Claude Opus 4";
        if (id.contains("sonnet-4")) return "Claude Sonnet 4";
        if (id.startsWith("claude-3-5-sonnet")) return "Claude 3.5 Sonnet";
        if (id.startsWith("claude-3-5-haiku")) return "Claude 3.5 Haiku";
        if (id.startsWith("claude-3-opus")) return "Claude 3 Opus";
        if (id.startsWith("claude-3-sonnet")) return "Claude 3 Sonnet";
        if (id.startsWith("claude-3-haiku")) return "Claude 3 Haiku";
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
        if (lower.contains("-exp")) {
            return false;
        }
        return lower.contains("flash") || lower.contains("pro");
    }

    private static boolean isRecommendedGeminiModel(String name) {
        return name != null && name.contains("2.5-flash") && !name.contains("lite");
    }

    private static int priorityForGemini(String name) {
        if (name == null) return 0;
        if (name.contains("2.5-flash") && !name.contains("lite")) return 130;
        if (name.contains("2.5-flash-lite")) return 120;
        if (name.contains("2.5-pro")) return 110;
        if (name.contains("3") && name.contains("flash")) return 105;
        if (name.contains("3") && name.contains("pro")) return 100;
        if (name.contains("2.0-flash")) return 80;
        return 10;
    }

    private static String formatGeminiName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "Gemini Model";
        }
        return name.replace('-', ' ').toUpperCase(Locale.US);
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
