package bin.mt.plugin.gemini;

/**
 * Constants for TranslateKit plugin
 *
 * @author Ilker Binzet
 * @version 0.4.0-beta
 * @updated June 2026 - Migrated to MT Plugin SDK v3 (3.0.0), fixed configuration builder
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
    public static final String PREF_BATCH_ENABLED = "gemini_batch_enabled";
    public static final String PREF_BATCH_SIZE = "gemini_batch_size";
    public static final String PREF_BATCH_MAX_CHARS = "gemini_batch_max_chars";
    public static final String PREF_BILINGUAL_MODE = "ai_bilingual_mode";

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
    public static final boolean DEFAULT_BATCH_ENABLED = true;
    public static final int DEFAULT_BATCH_SIZE = 25;
    public static final int DEFAULT_BATCH_MAX_CHARS = 10000;
    public static final boolean DEFAULT_BILINGUAL_MODE = false;
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

    // OpenRouter preference keys
    public static final String PREF_OPENROUTER_API_KEY = "openrouter_api_key";
    public static final String PREF_OPENROUTER_MODEL = "openrouter_model_name";
    public static final String PREF_OPENROUTER_ENDPOINT = "openrouter_api_endpoint";
    public static final String PREF_CACHE_OPENROUTER_MODELS = "cache_openrouter_models";

    // OpenRouter speaks the OpenAI chat/completions wire, so it needs no new
    // request path — only an endpoint, a key format and a model catalogue.
    public static final String DEFAULT_OPENROUTER_ENDPOINT =
            "https://openrouter.ai/api/v1/chat/completions";
    public static final String OPENROUTER_MODELS_ENDPOINT =
            "https://openrouter.ai/api/v1/models";
    public static final String OPENROUTER_API_KEY_PATTERN = "^sk-or-v1-[A-Za-z0-9]{16,}$";
    public static final String DEFAULT_OPENROUTER_MODEL = "google/gemini-2.5-flash";

    public static final String URL_OPENROUTER_KEYS = "https://openrouter.ai/keys";
    public static final String URL_OPENROUTER_DOCS = "https://openrouter.ai/docs";
    public static final String URL_OPENROUTER_PRICING = "https://openrouter.ai/models";

    // ==================== Default Model Seeds ====================
    // Single source of truth for "what models do we know about as of v0.4.0".
    // Used when no cache is present (first install, offline) and as fallback
    // when a live API fetch returns nothing. Columns:
    //   {id, displayName, description, recommended("true"/"false"), priority(int)}
    // Higher priority = sorted earlier in the UI. Live-fetched models are
    // merged with these by id; the live list always wins on conflict.
    // Declared here (after the model id constants) to avoid illegal forward
    // references in Java's static initialiser order.

    public static final String[][] GEMINI_SEED = {
            {MODEL_GEMINI_25_FLASH,      "Gemini 2.5 Flash",        "Stable, Recommended",         "true",  "130"},
            {MODEL_GEMINI_3_FLASH,       "Gemini 3 Flash (Preview)", "Pro-level at Flash speed",    "false", "115"},
            {MODEL_GEMINI_25_FLASH_LITE, "Gemini 2.5 Flash-Lite",   "Ultra Fast, Cost-efficient",  "false", "110"},
            {MODEL_GEMINI_3_PRO,         "Gemini 3 Pro (Preview)",  "Most Powerful, Multimodal",   "false", "108"},
            {MODEL_GEMINI_25_PRO,        "Gemini 2.5 Pro",          "Advanced reasoning, STEM",    "false", "100"},
    };

    public static final String[][] OPENAI_SEED = {
            {OPENAI_MODEL_GPT41_MINI, "GPT-4.1 Mini", "Fast, Recommended",   "true",  "120"},
            {OPENAI_MODEL_GPT52,      "GPT-5.2",      "Most Powerful",       "false", "115"},
            {OPENAI_MODEL_GPT51,      "GPT-5.1",      "Flagship",            "false", "110"},
            {OPENAI_MODEL_GPT5,       "GPT-5",        "High-end",            "false", "108"},
            {OPENAI_MODEL_GPT41,      "GPT-4.1",      "1M Context",          "false", "100"},
            {OPENAI_MODEL_GPT4O,      "GPT-4o",       "Omni, Multimodal",    "false",  "90"},
            {OPENAI_MODEL_GPT4O_MINI, "GPT-4o Mini",  "Economical",          "false",  "85"},
            {OPENAI_MODEL_O3,         "o3",           "Advanced Reasoning",  "false",  "80"},
            {OPENAI_MODEL_O4_MINI,    "o4-mini",      "Reasoning, Fast",     "false",  "78"},
            {OPENAI_MODEL_O3_MINI,    "o3-mini",      "Reasoning, Compact",  "false",  "75"},
    };

    public static final String[][] CLAUDE_SEED = {
            {CLAUDE_MODEL_SONNET_45, "Claude Sonnet 4.5",     "Balanced, Recommended",   "true",  "130"},
            {CLAUDE_MODEL_OPUS_46,   "Claude Opus 4.6",       "Most Powerful (Feb 2026)", "false", "125"},
            {CLAUDE_MODEL_HAIKU_45,  "Claude Haiku 4.5",      "Fast, Economical",        "false", "110"},
            {CLAUDE_MODEL_OPUS_45,   "Claude Opus 4.5",       "Previous Most Powerful",  "false", "100"},
            {CLAUDE_MODEL_SONNET_4,  "Claude Sonnet 4",       "Previous Balanced",       "false",  "90"},
            {CLAUDE_MODEL_OPUS_4,    "Claude Opus 4",         "Legacy",                  "false",  "80"},
    };

    public static final String[][] OPENROUTER_SEED = {
            {"google/gemini-2.5-flash",           "Gemini 2.5 Flash",  "Fast, Recommended", "true",  "130"},
            {"anthropic/claude-sonnet-4.5",       "Claude Sonnet 4.5", "Balanced",          "false", "120"},
            {"openai/gpt-4.1-mini",               "GPT-4.1 Mini",      "Economical",        "false", "115"},
            {"deepseek/deepseek-chat",            "DeepSeek Chat",     "Very low cost",     "false", "105"},
            {"meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B",     "Open weights",      "false",  "95"},
    };

    /** Custom model override preference key suffix (per provider). */
    public static final String PREF_CUSTOM_MODEL = "_custom_model";

    // ==================== Engine Identifiers ====================

    public static final String ENGINE_GEMINI = "gemini";
    public static final String ENGINE_OPENAI = "openai";
    public static final String ENGINE_CLAUDE = "claude";
    public static final String ENGINE_OPENROUTER = "openrouter";

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
    public static final int PLUGIN_VERSION_CODE = 9;
    public static final String PLUGIN_VERSION_NAME = "0.4.0-beta4";

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
