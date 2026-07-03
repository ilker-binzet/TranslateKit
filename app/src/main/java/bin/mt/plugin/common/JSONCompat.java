package bin.mt.plugin.common;

import bin.mt.json.JSONArray;
import bin.mt.json.JSONObject;

/**
 * Compatibility shim between {@code org.json}-style call sites (which use
 * {@code optXxx(key, default)} and {@code length()}) and the SDK 3.0
 * {@code bin.mt.json} library (which exposes {@code getXxx(key)} that throws
 * on missing keys, plus {@code size()} on arrays and {@code add(value)}
 * for appending).
 *
 * <p>The plugin was originally written against {@code org.json} which ships
 * with Android. v3 stable replaces it with {@code bin.mt.json}, whose API
 * is more Java-idiomatic but slightly different. This helper bridges the
 * two so call sites can stay readable.</p>
 *
 * <p>All {@code getXxx} calls inside the wrappers are wrapped in
 * {@code try/catch} so the original {@code org.json} "return default on
 * missing key" behaviour is preserved without needing a {@code has(key)}
 * pre-check (the v3 library uses {@code contains(key)} or throws on
 * missing keys depending on the build).</p>
 */
public final class JSONCompat {

    private JSONCompat() {}

    // ----------------- JSONObject safe accessors -----------------

    /** Returns {@code default} when the key is missing or not a string. */
    public static String optString(JSONObject obj, String key, String defaultValue) {
        if (obj == null) return defaultValue;
        try { return obj.getString(key); } catch (Exception e) { return defaultValue; }
    }

    /** Returns {@code default} when the key is missing or not an int. */
    public static int optInt(JSONObject obj, String key, int defaultValue) {
        if (obj == null) return defaultValue;
        try { return obj.getInt(key); } catch (Exception e) { return defaultValue; }
    }

    /** Returns {@code default} when the key is missing or not a long. */
    public static long optLong(JSONObject obj, String key, long defaultValue) {
        if (obj == null) return defaultValue;
        try { return obj.getLong(key); } catch (Exception e) { return defaultValue; }
    }

    /** Returns {@code default} when the key is missing or not a boolean. */
    public static boolean optBoolean(JSONObject obj, String key, boolean defaultValue) {
        if (obj == null) return defaultValue;
        try { return obj.getBoolean(key); } catch (Exception e) { return defaultValue; }
    }

    /** Returns null when the key is missing. */
    public static JSONObject optJSONObject(JSONObject obj, String key) {
        if (obj == null) return null;
        try { return obj.getJSONObject(key); } catch (Exception e) { return null; }
    }

    /** Returns null when the key is missing. */
    public static JSONArray optJSONArray(JSONObject obj, String key) {
        if (obj == null) return null;
        try { return obj.getJSONArray(key); } catch (Exception e) { return null; }
    }

    /**
     * Returns true when the key exists in the object. The v3 library
     * exposes {@code contains(String)} for this — the org.json name
     * {@code has(String)} isn't part of {@code bin.mt.json}.
     */
    public static boolean has(JSONObject obj, String key) {
        if (obj == null || key == null) return false;
        return obj.contains(key);
    }

    /**
     * Return the keys of a JSONObject as a {@code List<String>}.
     * The v3 lib exposes {@code names()} for this; we copy to a mutable
     * list so callers can safely sort or remove entries without touching
     * the underlying storage.
     */
    public static java.util.List<String> keys(JSONObject obj) {
        if (obj == null) return new java.util.ArrayList<>();
        java.util.List<String> names = obj.names();
        if (names == null) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(names);
    }

    // ----------------- JSONArray helpers -----------------

    /** {@code size()} of the array, or 0 when the array is null. */
    public static int size(JSONArray array) {
        if (array == null) return 0;
        try { return array.size(); } catch (Exception e) { return 0; }
    }

    /** Index-safe getter: null when out of range. */
    public static JSONObject optJSONObject(JSONArray array, int index) {
        if (array == null || index < 0 || index >= size(array)) return null;
        try { return array.getJSONObject(index); } catch (Exception e) { return null; }
    }

    /** Index-safe getter: null when out of range. */
    public static String optString(JSONArray array, int index, String defaultValue) {
        if (array == null || index < 0 || index >= size(array)) return defaultValue;
        try { return array.getString(index); } catch (Exception e) { return defaultValue; }
    }

    /**
     * Append a JSONObject to a JSONArray. The new SDK only accepts
     * primitive types and {@code JSONValue} for {@code add(...)}; a
     * {@code JSONObject} is a {@code JSONValue} subclass so this works
     * directly — the cast is implicit.
     */
    public static void put(JSONArray array, JSONObject value) {
        if (array == null) return;
        array.add(value);
    }
}
