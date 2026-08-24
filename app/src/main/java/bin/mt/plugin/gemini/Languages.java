package bin.mt.plugin.gemini;

import android.content.SharedPreferences;

import bin.mt.plugin.api.PluginContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The languages the engine offers, and the names shown for them.
 *
 * <p>Two reasons this catalogue is a class rather than an array on the engine:
 * the settings screen needs the same list to build its picker, and MT has no
 * built-in name for every code we support — Hebrew showed up in the language
 * dropdown as a bare "he" until the names here filled the gap.
 *
 * <p>The preference stores enabled codes as a comma-separated list. Parsing is
 * kept free of Android types so it can be exercised in unit tests; only the
 * two {@code SharedPreferences} wrappers touch storage.
 *
 * @author Ilker Binzet
 */
public final class Languages {

    /** Comma-separated ISO 639-1 codes. Absent or empty means every language. */
    public static final String PREF_ENABLED_LANGUAGES = "enabled_languages";

    /** Code to English name, in the order the pickers show them. */
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("en", "English");
        NAMES.put("tr", "Turkish");
        NAMES.put("de", "German");
        NAMES.put("fr", "French");
        NAMES.put("es", "Spanish");
        NAMES.put("it", "Italian");
        NAMES.put("pt", "Portuguese");
        NAMES.put("ru", "Russian");
        NAMES.put("ja", "Japanese");
        NAMES.put("ko", "Korean");
        NAMES.put("zh-CN", "Chinese (Simplified)");
        NAMES.put("zh-TW", "Chinese (Traditional)");
        NAMES.put("ar", "Arabic");
        NAMES.put("hi", "Hindi");
        NAMES.put("nl", "Dutch");
        NAMES.put("sv", "Swedish");
        NAMES.put("pl", "Polish");
        NAMES.put("uk", "Ukrainian");
        NAMES.put("cs", "Czech");
        NAMES.put("el", "Greek");
        NAMES.put("he", "Hebrew");
        NAMES.put("id", "Indonesian");
        NAMES.put("th", "Thai");
        NAMES.put("vi", "Vietnamese");
        NAMES.put("ro", "Romanian");
        NAMES.put("hu", "Hungarian");
        NAMES.put("da", "Danish");
        NAMES.put("fi", "Finnish");
        NAMES.put("no", "Norwegian");
        NAMES.put("bg", "Bulgarian");
        NAMES.put("hr", "Croatian");
        NAMES.put("sr", "Serbian");
        NAMES.put("sk", "Slovak");
        NAMES.put("sl", "Slovenian");
        NAMES.put("lt", "Lithuanian");
        NAMES.put("lv", "Latvian");
        NAMES.put("et", "Estonian");
    }

    private Languages() {
        throw new AssertionError("Cannot instantiate");
    }

    /** Every supported code, in catalogue order. */
    public static List<String> allCodes() {
        return new ArrayList<>(NAMES.keySet());
    }

    /**
     * The name to show for a code, translated when the language pack carries
     * an entry for it. Entries are addressed as {@code lang_<code>}.
     */
    public static String displayName(PluginContext context, String code) {
        String translated = context.getString("{lang_" + code + "}");
        // A pack with no entry hands back something unusable rather than a name.
        if (translated == null || translated.isEmpty() || translated.contains("{")) {
            String english = nameOf(code);
            return english != null ? english : code;
        }
        return translated;
    }

    /** English name for a code, or null when the code is not in the catalogue. */
    public static String nameOf(String code) {
        return NAMES.get(code);
    }

    /**
     * The codes to offer in the language pickers.
     *
     * <p>Unknown codes are dropped and catalogue order is restored, so neither
     * an imported preset nor the order things were ticked in can affect what
     * the pickers show. An empty selection falls back to every language rather
     * than handing MT an empty list.
     */
    public static List<String> parseEnabled(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return allCodes();
        }
        List<String> requested = new ArrayList<>();
        Collections.addAll(requested, csv.split(","));

        List<String> result = new ArrayList<>();
        for (String code : NAMES.keySet()) {
            for (String candidate : requested) {
                if (code.equals(candidate.trim())) {
                    result.add(code);
                    break;
                }
            }
        }
        return result.isEmpty() ? allCodes() : result;
    }

    /** Serialises a selection for storage. */
    public static String joinEnabled(List<String> codes) {
        StringBuilder sb = new StringBuilder();
        for (String code : codes) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(code);
        }
        return sb.toString();
    }

    public static List<String> enabled(SharedPreferences prefs) {
        return parseEnabled(prefs.getString(PREF_ENABLED_LANGUAGES, ""));
    }

    public static void saveEnabled(SharedPreferences prefs, List<String> codes) {
        prefs.edit().putString(PREF_ENABLED_LANGUAGES, joinEnabled(codes)).apply();
    }
}
