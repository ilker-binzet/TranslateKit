package bin.mt.plugin.gemini;

/**
 * Constants for Gemini AI Translation Plugin
 *
 * @author MT Manager Plugin Developer
 * @version 0.1.0
 */
public class GeminiConstants {

    /**
     * Gemini API base URL
     * Documentation: https://ai.google.dev/gemini-api/docs/text-generation
     */
    public static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    // ==================== Model Names ====================

    /**
     * Gemini 2.5 Flash - Fast and efficient (RECOMMENDED for translation)
     * Latest stable model with best speed/quality balance
     */
    public static final String MODEL_GEMINI_25_FLASH = "gemini-2.5-flash";

    /**
     * Gemini 2.5 Flash Lite - Even faster, good for simple translations
     * Most efficient for high-volume translation
     */
    public static final String MODEL_GEMINI_25_FLASH_LITE = "gemini-2.5-flash-lite";

    /**
     * Gemini 2.5 Pro - Highest quality (slower, more strict rate limits)
     * Best for complex or nuanced translations
     */
    public static final String MODEL_GEMINI_25_PRO = "gemini-2.5-pro";

    /**
     * Gemini 2.0 Flash - Previous stable version
     * Good fallback option
     */
    public static final String MODEL_GEMINI_20_FLASH = "gemini-2.0-flash";

    /**
     * Default model for translation - best balance
     */
    public static final String DEFAULT_MODEL = MODEL_GEMINI_25_FLASH;

    // ==================== Preference Keys ====================

    public static final String PREF_API_KEY = "gemini_api_key";
    public static final String PREF_MODEL_NAME = "gemini_model_name";
    public static final String PREF_TIMEOUT = "gemini_request_timeout";
    public static final String PREF_MAX_RETRIES = "gemini_max_retries";
    public static final String PREF_TEMPERATURE = "gemini_temperature";
    public static final String PREF_ENABLE_CACHE = "gemini_enable_cache";
    public static final String PREF_DEFAULT_ENGINE = "ai_default_engine";
    public static final String PREF_ENABLE_DEBUG = "ai_enable_debug_logging";
    public static final String PREF_CONTEXT_APP_NAME = "ai_context_app_name";
    public static final String PREF_CONTEXT_APP_TYPE = "ai_context_app_type";
    public static final String PREF_CONTEXT_AUDIENCE = "ai_context_target_audience";
    public static final String PREF_CONTEXT_TONE = "ai_context_tone";
    public static final String PREF_CONTEXT_NOTES = "ai_context_custom_notes";

    // OpenAI preference keys
    public static final String PREF_OPENAI_API_KEY = "openai_api_key";
    public static final String PREF_OPENAI_MODEL = "openai_model_name";
    public static final String PREF_OPENAI_ENDPOINT = "openai_api_endpoint";

    // Claude preference keys
    public static final String PREF_CLAUDE_API_KEY = "claude_api_key";
    public static final String PREF_CLAUDE_MODEL = "claude_model_name";
    public static final String PREF_CLAUDE_ENDPOINT = "claude_api_endpoint";
    
    // Cached model catalogs
    public static final String PREF_CACHE_OPENAI_MODELS = "cache_openai_models";
    public static final String PREF_CACHE_CLAUDE_MODELS = "cache_claude_models";
    public static final String PREF_CACHE_GEMINI_MODELS = "cache_gemini_models";
    public static final String PREF_DEBUG_DISABLE_MODEL_CACHE = "debug_disable_model_cache";

    public static final long MODEL_CACHE_TTL_MS = 6 * 60 * 60 * 1000L; // 6 hours

    // Claude API version constant
    public static final String CLAUDE_API_VERSION = "2023-06-01";

    // ==================== Default Values ====================

    public static final String DEFAULT_API_KEY = "";
    public static final int DEFAULT_TIMEOUT = 30000; // 30 seconds
    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final float DEFAULT_TEMPERATURE = 0.1f; // Low for consistent translation
    public static final String DEFAULT_ENGINE = "gemini";
    public static final boolean DEFAULT_ENABLE_DEBUG = false;
    public static final String CLAUDE_MODEL_FALLBACK = "claude-3-sonnet-20240229";
    public static final String DEFAULT_CONTEXT_TONE = "Clear and instructional";

    public static final String DEFAULT_OPENAI_MODEL = "gpt-4o";
    public static final String DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    public static final String DEFAULT_CLAUDE_MODEL = "claude-3-5-sonnet-20241022";
    public static final String DEFAULT_CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages";

    // ==================== Engine Identifiers ====================

    public static final String ENGINE_GEMINI = "gemini";
    public static final String ENGINE_OPENAI = "openai";
    public static final String ENGINE_CLAUDE = "claude";

    // ==================== Rate Limits (Free Tier) ====================

    /**
     * Gemini 1.5 Flash / Gemini 1.0 Pro limits
     */
    public static final int RATE_LIMIT_RPM_FLASH = 15; // Requests per minute
    public static final int RATE_LIMIT_RPD_FLASH = 1500; // Requests per day
    public static final int RATE_LIMIT_TPD_FLASH = 1_000_000; // Tokens per day

    /**
     * Gemini 1.5 Pro limits (more restrictive)
     */
    public static final int RATE_LIMIT_RPM_PRO = 2;
    public static final int RATE_LIMIT_RPD_PRO = 50;

    // ==================== URLs ====================

    public static final String URL_GET_API_KEY = "https://aistudio.google.com/app/apikey";
    public static final String URL_API_DOCS = "https://ai.google.dev/gemini-api/docs";
    public static final String URL_PRICING = "https://ai.google.dev/pricing";

    public static final String URL_OPENAI_KEYS = "https://platform.openai.com/api-keys";
    public static final String URL_OPENAI_DOCS = "https://platform.openai.com/docs";
    public static final String URL_OPENAI_PRICING = "https://openai.com/api/pricing";

    public static final String URL_CLAUDE_KEYS = "https://console.anthropic.com/account/keys";
    public static final String URL_CLAUDE_DOCS = "https://docs.anthropic.com";
    public static final String URL_CLAUDE_PRICING = "https://www.anthropic.com/pricing";
    public static final String CLAUDE_MODELS_ENDPOINT = "https://api.anthropic.com/v1/models";

    // ==================== Plugin Metadata ====================

    public static final String PLUGIN_ID = "mt.plugin.ai.hub";
    public static final int PLUGIN_VERSION_CODE = 100;
    public static final String PLUGIN_VERSION_NAME = "0.1.0";

    // ==================== API Key Pattern ====================

    /**
     * Gemini API keys start with "AIzaSy" and are 39 characters
     * Same format as other Google API keys
     */
    public static final String API_KEY_PATTERN = "^AIzaSy[A-Za-z0-9_-]{33}$";
    public static final String OPENAI_API_KEY_PATTERN = "^sk-[A-Za-z0-9_-]{16,}$";
    public static final String CLAUDE_API_KEY_PATTERN = "^sk-ant-[A-Za-z0-9_-]{16,}$";

    // ==================== Developer Info ====================

    public static final String DEVELOPER_NAME = "Ilker Binzet";
    public static final String DEVELOPER_GITHUB = "https://github.com/ilker-binzet";
    public static final String DEVELOPER_LINKEDIN = "https://www.linkedin.com/in/binzet-me";

    // Constructor
    private GeminiConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
