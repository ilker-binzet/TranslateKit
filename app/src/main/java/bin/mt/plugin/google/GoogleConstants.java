package bin.mt.plugin.google;

/**
 * Constants for Google Cloud Translation API Plugin
 *
 * This class contains all configuration constants, API endpoints,
 * preference keys, and default values used by the plugin.
 *
 * @author MT Manager Plugin Developer
 * @version 1.0.0
 */
public class GoogleConstants {

    /**
     * Google Cloud Translation API v2 (Basic) endpoint
     * Documentation: https://cloud.google.com/translate/docs/reference/rest/v2/translate
     */
    public static final String API_BASE_URL = "https://translation.googleapis.com/language/translate/v2";

    /**
     * Alternative endpoint for API v3 (Advanced)
     * Requires OAuth2 authentication instead of API key
     */
    public static final String API_V3_BASE_URL = "https://translation.googleapis.com/v3/projects/{PROJECT_ID}/locations/{LOCATION}:translateText";

    // ==================== Preference Keys ====================

    /**
     * Preference key for Google Cloud API key
     * Users must obtain this from Google Cloud Console
     */
    public static final String PREF_API_KEY = "google_cloud_api_key";

    /**
     * Preference key for default target language
     */
    public static final String PREF_DEFAULT_TARGET_LANG = "google_default_target_lang";

    /**
     * Preference key for using advanced NMT model
     */
    public static final String PREF_USE_ADVANCED_MODEL = "google_use_advanced_model";

    /**
     * Preference key for request timeout (milliseconds)
     */
    public static final String PREF_TIMEOUT = "google_request_timeout";

    /**
     * Preference key for maximum retry attempts
     */
    public static final String PREF_MAX_RETRIES = "google_max_retries";

    /**
     * Preference key for enabling request caching
     */
    public static final String PREF_ENABLE_CACHE = "google_enable_cache";

    /**
     * Preference key for cache expiration time (minutes)
     */
    public static final String PREF_CACHE_EXPIRATION = "google_cache_expiration";

    // ==================== Default Values ====================

    /**
     * Default API key (empty - user must provide)
     */
    public static final String DEFAULT_API_KEY = "";

    /**
     * Default target language (English)
     */
    public static final String DEFAULT_TARGET_LANG = "en";

    /**
     * Default request timeout: 30 seconds
     */
    public static final int DEFAULT_TIMEOUT = 30000;

    /**
     * Default maximum retry attempts: 2
     */
    public static final int DEFAULT_MAX_RETRIES = 2;

    /**
     * Default cache expiration: 60 minutes
     */
    public static final int DEFAULT_CACHE_EXPIRATION = 60;

    // ==================== API Limits ====================

    /**
     * Maximum characters per single translation request
     * Google recommends keeping requests under 5000 characters
     */
    public static final int MAX_TEXT_LENGTH = 5000;

    /**
     * Maximum batch size for multiple translations
     * (For future batch implementation)
     */
    public static final int MAX_BATCH_SIZE = 128;

    /**
     * Rate limit: requests per second (depends on quota tier)
     * Free tier: 10 requests/second
     * Paid tier: configurable up to 1000 requests/second
     */
    public static final int DEFAULT_RATE_LIMIT = 10;

    // ==================== HTTP Headers ====================

    /**
     * User-Agent header for API requests
     */
    public static final String USER_AGENT = "MTManager-GoogleTranslate-Plugin/1.0";

    /**
     * Content-Type for JSON requests
     */
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    // ==================== Error Codes ====================

    /**
     * Google Cloud Translation API error codes
     * Reference: https://cloud.google.com/translate/docs/reference/rest/v2/translate#response-codes
     */
    public static final int ERROR_CODE_INVALID_ARGUMENT = 400;
    public static final int ERROR_CODE_UNAUTHENTICATED = 401;
    public static final int ERROR_CODE_PERMISSION_DENIED = 403;
    public static final int ERROR_CODE_NOT_FOUND = 404;
    public static final int ERROR_CODE_RATE_LIMIT = 429;
    public static final int ERROR_CODE_INTERNAL_ERROR = 500;
    public static final int ERROR_CODE_SERVICE_UNAVAILABLE = 503;

    // ==================== Cache Keys ====================

    /**
     * Cache key prefix for translated texts
     */
    public static final String CACHE_KEY_PREFIX = "google_translate_cache_";

    /**
     * Preference key for cache hit statistics
     */
    public static final String PREF_CACHE_HITS = "google_cache_hits";

    /**
     * Preference key for cache miss statistics
     */
    public static final String PREF_CACHE_MISSES = "google_cache_misses";

    // ==================== URLs ====================

    /**
     * Google Cloud Console URL for API key creation
     */
    public static final String URL_GOOGLE_CONSOLE = "https://console.cloud.google.com/apis/credentials";

    /**
     * Google Cloud Translation API documentation
     */
    public static final String URL_API_DOCS = "https://cloud.google.com/translate/docs";

    /**
     * Google Cloud Translation pricing page
     */
    public static final String URL_PRICING = "https://cloud.google.com/translate/pricing";

    /**
     * Plugin GitHub repository (replace with your actual repository)
     */
    public static final String URL_PLUGIN_GITHUB = "https://github.com/yourusername/mt-google-translate-plugin";

    // ==================== Plugin Metadata ====================

    /**
     * Plugin ID - must be unique across all MT Manager plugins
     */
    public static final String PLUGIN_ID = "bin.mt.plugin.google.translate";

    /**
     * Plugin version code (increment for updates)
     */
    public static final int PLUGIN_VERSION_CODE = 1;

    /**
     * Plugin version name (semantic versioning)
     */
    public static final String PLUGIN_VERSION_NAME = "1.0.0";

    // ==================== Validation Patterns ====================

    /**
     * Pattern for validating Google Cloud API keys
     * Format: AIzaSy[A-Za-z0-9_-]{33}
     */
    public static final String API_KEY_PATTERN = "^AIzaSy[A-Za-z0-9_-]{33}$";

    // ==================== Feature Flags ====================

    /**
     * Enable/disable batch translation feature (future implementation)
     */
    public static final boolean FEATURE_BATCH_TRANSLATION = false;

    /**
     * Enable/disable translation caching
     */
    public static final boolean FEATURE_CACHE = true;

    /**
     * Enable/disable usage statistics
     */
    public static final boolean FEATURE_STATISTICS = true;

    /**
     * Enable/disable debug logging
     */
    public static final boolean DEBUG_LOGGING = false;

    // ==================== Constructor (prevent instantiation) ====================

    private GoogleConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
