package bin.mt.plugin.gemini;

/**
 * Constants for TranslateKit plugin
 *
 * @author MT Manager Plugin Developer
 * @version 0.2.0-alpha
 * @updated February 2026 - BaseBatchTranslationEngine, latest AI models & SDK beta3
 */
public class GeminiConstants {

    /**
     * Gemini API base URL
     * Documentation: https://ai.google.dev/gemini-api/docs/text-generation
     */
    public static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    // ==================== Gemini Model Names (Updated February 2026) ====================

    /**
     * Gemini 3 Pro (Preview) - Most powerful model
     * Best for complex multimodal understanding and agentic tasks
     */
    public static final String MODEL_GEMINI_3_PRO = "gemini-3-pro-preview";

    /**
     * Gemini 3 Flash (Preview) - Pro-level intelligence at Flash speed
     * Best balance of speed, cost and intelligence
     */
    public static final String MODEL_GEMINI_3_FLASH = "gemini-3-flash-preview";

    /**
     * Gemini 2.5 Flash - Stable, fast model (RECOMMENDED for translation)
     * Best price-performance ratio, versatile capabilities
     */
    public static final String MODEL_GEMINI_25_FLASH = "gemini-2.5-flash";

    /**
     * Gemini 2.5 Flash-Lite - Ultra-fast, cost-efficient
     * Optimized for high throughput and low latency
     */
    public static final String MODEL_GEMINI_25_FLASH_LITE = "gemini-2.5-flash-lite";

    /**
     * Gemini 2.5 Pro - Advanced thinking model
     * For complex reasoning, math, STEM and large codebase analysis
     */
    public static final String MODEL_GEMINI_25_PRO = "gemini-2.5-pro";

    /**
     * Default model for translation - best stability (Gemini 2.5 Flash)
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
    public static final String PREF_DEFAULT_TARGET_LANG = "ai_default_target_lang";
    public static final String PREF_BATCH_SIZE = "gemini_batch_size";
    public static final String PREF_BATCH_MAX_CHARS = "gemini_batch_max_chars";

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
    public static final int DEFAULT_BATCH_SIZE = 25;
    public static final int DEFAULT_BATCH_MAX_CHARS = 10000;
    public static final String CLAUDE_MODEL_FALLBACK = "claude-sonnet-4-5-latest";
    public static final String DEFAULT_CONTEXT_TONE = "Clear and instructional";

    // OpenAI Models (Updated February 2026)
    // GPT-5.x family: gpt-5.2, gpt-5.1, gpt-5
    // GPT-4.1 family: gpt-4.1, gpt-4.1-mini (1M context)
    // GPT-4o family: gpt-4o, gpt-4o-mini
    // O-series reasoning: o3, o4-mini, o3-mini
    public static final String DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";
    public static final String OPENAI_MODEL_GPT52 = "gpt-5.2";
    public static final String OPENAI_MODEL_GPT51 = "gpt-5.1";
    public static final String OPENAI_MODEL_GPT5 = "gpt-5";
    public static final String OPENAI_MODEL_GPT41 = "gpt-4.1";
    public static final String OPENAI_MODEL_GPT41_MINI = "gpt-4.1-mini";
    public static final String OPENAI_MODEL_GPT4O = "gpt-4o";
    public static final String OPENAI_MODEL_GPT4O_MINI = "gpt-4o-mini";
    public static final String OPENAI_MODEL_O3 = "o3";
    public static final String OPENAI_MODEL_O4_MINI = "o4-mini";
    public static final String OPENAI_MODEL_O3_MINI = "o3-mini";
    public static final String DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    // Claude Models (Updated February 2026)
    // Claude Opus 4.6 (newest, Feb 2026), 4.5 family, 4 family
    // API naming: claude-{tier}-{version} (e.g. claude-opus-4-6)
    public static final String DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-5-latest";
    public static final String CLAUDE_MODEL_OPUS_46 = "claude-opus-4-6";
    public static final String CLAUDE_MODEL_OPUS_45 = "claude-opus-4-5-latest";
    public static final String CLAUDE_MODEL_SONNET_45 = "claude-sonnet-4-5-latest";
    public static final String CLAUDE_MODEL_HAIKU_45 = "claude-haiku-4-5-latest";
    public static final String CLAUDE_MODEL_OPUS_4 = "claude-opus-4-latest";
    public static final String CLAUDE_MODEL_SONNET_4 = "claude-sonnet-4-latest";
    public static final String DEFAULT_CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages";

    // ==================== Engine Identifiers ====================

    public static final String ENGINE_GEMINI = "gemini";
    public static final String ENGINE_OPENAI = "openai";
    public static final String ENGINE_CLAUDE = "claude";

    // ==================== Rate Limits (Free Tier - Updated 2026)
    // ====================

    /**
     * Gemini 3 Flash limits (free tier)
     */
    public static final int RATE_LIMIT_RPM_FLASH = 30; // Requests per minute
    public static final int RATE_LIMIT_RPD_FLASH = 2000; // Requests per day
    public static final int RATE_LIMIT_TPD_FLASH = 2_000_000; // Tokens per day

    /**
     * Gemini 3 Pro limits (more restrictive)
     */
    public static final int RATE_LIMIT_RPM_PRO = 5;
    public static final int RATE_LIMIT_RPD_PRO = 100;

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

    public static final String PLUGIN_ID = "mt.plugin.translatekit";
    public static final int PLUGIN_VERSION_CODE = 2;
    public static final String PLUGIN_VERSION_NAME = "0.2.0-alpha";

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

    // ==================== Material Icons (UI Modernization) ====================

    // Dashboard & Navigation Icons
    public static final String ICON_DASHBOARD = "dashboard";
    public static final String ICON_SETTINGS = "settings";
    public static final String ICON_INFO = "info";
    public static final String ICON_HELP = "help";
    public static final String ICON_HOME = "home";

    // Provider & AI Icons
    public static final String ICON_SMART_TOY = "smart_toy";          // AI/Bot icon
    public static final String ICON_PSYCHOLOGY = "psychology";        // AI brain icon
    public static final String ICON_ROBOT = "smart_toy";              // Robot icon (alias)

    // Action Icons
    public static final String ICON_REFRESH = "refresh";
    public static final String ICON_COPY = "content_copy";
    public static final String ICON_PASTE = "content_paste";
    public static final String ICON_DOWNLOAD = "download";
    public static final String ICON_UPLOAD = "upload";
    public static final String ICON_SHARE = "share";
    public static final String ICON_DELETE = "delete";
    public static final String ICON_EDIT = "edit";
    public static final String ICON_ADD = "add";
    public static final String ICON_REMOVE = "remove";
    public static final String ICON_CHECK = "check";
    public static final String ICON_CLOSE = "close";

    // Category Icons
    public static final String ICON_KEY = "key";                      // API Configuration
    public static final String ICON_STARS = "stars";                  // Model Selection
    public static final String ICON_PALETTE = "palette";              // Context & Tone
    public static final String ICON_BUILD = "build";                  // Debug & Advanced
    public static final String ICON_TRANSLATE = "translate";          // Translation
    public static final String ICON_LANGUAGE = "language";            // Language settings
    public static final String ICON_CODE = "code";                    // Code/Technical
    public static final String ICON_MENU_BOOK = "menu_book";          // Resources/Docs

    // Status Icons
    public static final String ICON_CHECK_CIRCLE = "check_circle";    // Success
    public static final String ICON_ERROR = "error";                  // Error
    public static final String ICON_WARNING = "warning";              // Warning
    public static final String ICON_PENDING = "pending";              // Pending
    public static final String ICON_SYNC = "sync";                    // Syncing
    public static final String ICON_CLOUD_DONE = "cloud_done";        // Cloud success
    public static final String ICON_CLOUD_OFF = "cloud_off";          // Cloud offline

    // Feature Icons
    public static final String ICON_SPEED = "speed";                  // Performance
    public static final String ICON_TIMER = "timer";                  // Timeout
    public static final String ICON_HISTORY = "history";              // History
    public static final String ICON_SEARCH = "search";                // Search
    public static final String ICON_FILTER = "filter_list";           // Filter
    public static final String ICON_SORT = "sort";                    // Sort
    public static final String ICON_VISIBILITY = "visibility";        // Show
    public static final String ICON_VISIBILITY_OFF = "visibility_off";// Hide

    // Document & Text Icons
    public static final String ICON_TEXT_SNIPPET = "text_snippet";    // Text/Document
    public static final String ICON_ARTICLE = "article";              // Article
    public static final String ICON_NOTE = "note";                    // Note
    public static final String ICON_FORMAT_SIZE = "format_size";      // Font size

    // Network & API Icons
    public static final String ICON_CLOUD = "cloud";                  // Cloud/API
    public static final String ICON_CLOUD_UPLOAD = "cloud_upload";    // API upload
    public static final String ICON_CLOUD_DOWNLOAD = "cloud_download";// API download
    public static final String ICON_SIGNAL = "signal_cellular_alt";   // Signal strength

    // Advanced Icons
    public static final String ICON_BUG_REPORT = "bug_report";        // Debug
    public static final String ICON_SCIENCE = "science";              // Experimental
    public static final String ICON_TUNE = "tune";                    // Fine-tune
    public static final String ICON_EXTENSION = "extension";          // Extensions/Plugins

    // UI Layout Icons
    public static final String ICON_ARROW_FORWARD = "arrow_forward";
    public static final String ICON_ARROW_BACK = "arrow_back";
    public static final String ICON_ARROW_DROP_DOWN = "arrow_drop_down";
    public static final String ICON_EXPAND_MORE = "expand_more";
    public static final String ICON_EXPAND_LESS = "expand_less";
    public static final String ICON_MORE_VERT = "more_vert";          // Three dots vertical
    public static final String ICON_MORE_HORIZ = "more_horiz";        // Three dots horizontal

    // Constructor
    private GeminiConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
