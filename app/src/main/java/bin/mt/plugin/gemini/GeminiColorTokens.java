package bin.mt.plugin.gemini;

import bin.mt.plugin.api.ui.PluginUI;

/**
 * Theme-aware color token system for TranslateKit.
 * Provides consistent colors across light and dark themes.
 */
public class GeminiColorTokens {

    // ==================== Provider Brand Colors ====================

    /**
     * Get theme-aware provider brand color.
     * Returns lighter colors for dark theme, standard colors for light theme.
     *
     * @param ui       PluginUI instance for theme detection
     * @param provider Provider name: "gemini", "openai", "claude", "google"
     * @return Color int value
     */
    public static int getProviderBrandColor(PluginUI ui, String provider) {
        boolean isDark = ui.isDarkTheme();

        return switch (provider.toLowerCase()) {
            case "gemini" -> isDark ? 0xFF4285F4 : 0xFF1A73E8;  // Google Blue
            case "openai" -> isDark ? 0xFF10A37F : 0xFF0B8F6A;  // OpenAI Green
            case "claude" -> isDark ? 0xFFD97757 : 0xFFB55F3B;  // Anthropic Orange
            case "google" -> isDark ? 0xFF34A853 : 0xFF2D8C43;  // Google Green
            default -> ui.colorAccent();  // Fallback to system accent
        };
    }

    /**
     * Get provider icon color with theme awareness.
     * Same as brand color but optimized for icon rendering.
     *
     * @param ui       PluginUI instance
     * @param provider Provider name
     * @return Color int for icon tinting
     */
    public static int getProviderIconColor(PluginUI ui, String provider) {
        // Icons use same color as brand
        return getProviderBrandColor(ui, provider);
    }

    // ==================== Status Colors ====================

    /**
     * Get color for status indicators.
     * Maps status types to appropriate SDK color methods.
     *
     * @param ui     PluginUI instance
     * @param status Status type: "ready", "error", "warning", "neutral", "success"
     * @return Color int value
     */
    public static int getStatusColor(PluginUI ui, String status) {
        return switch (status.toLowerCase()) {
            case "ready", "success", "active" -> ui.colorPrimary();
            case "error", "failed", "invalid" -> ui.colorError();
            case "warning", "pending", "limited" -> ui.colorWarning();
            case "neutral", "not_configured", "unknown" -> ui.colorTextSecondary();
            default -> ui.colorText();
        };
    }

    /**
     * Get text color for status messages.
     * Returns primary color for positive states, error color for negative.
     *
     * @param ui     PluginUI instance
     * @param status Status type
     * @return Color int for text
     */
    public static int getStatusTextColor(PluginUI ui, String status) {
        // For most statuses, use same color as status indicator
        return getStatusColor(ui, status);
    }

    // ==================== UI Element Colors ====================

    /**
     * Get card background color based on theme.
     *
     * @param ui PluginUI instance
     * @return Color int for card backgrounds
     */
    public static int getCardBackgroundColor(PluginUI ui) {
        return ui.isDarkTheme() ? 0xFF2C2C2C : 0xFFF5F5F5;
    }

    /**
     * Get emphasized card background (for active/selected cards).
     *
     * @param ui PluginUI instance
     * @return Color int for emphasized backgrounds
     */
    public static int getCardBackgroundEmphasizedColor(PluginUI ui) {
        return ui.isDarkTheme() ? 0xFF3C3C3C : 0xFFE8E8E8;
    }

    /**
     * Get divider color.
     *
     * @param ui PluginUI instance
     * @return Color int for dividers
     */
    public static int getDividerColor(PluginUI ui) {
        return ui.colorDivider();
    }

    /**
     * Get secondary text color.
     *
     * @param ui PluginUI instance
     * @return Color int for secondary text
     */
    public static int getSecondaryTextColor(PluginUI ui) {
        return ui.colorTextSecondary();
    }

    /**
     * Get primary text color.
     *
     * @param ui PluginUI instance
     * @return Color int for primary text
     */
    public static int getPrimaryTextColor(PluginUI ui) {
        return ui.colorText();
    }

    // ==================== Semantic Colors ====================

    /**
     * Get color for informational messages.
     *
     * @param ui PluginUI instance
     * @return Color int for info messages
     */
    public static int getInfoColor(PluginUI ui) {
        return ui.isDarkTheme() ? 0xFF64B5F6 : 0xFF2196F3;  // Blue
    }

    /**
     * Get color for success messages/indicators.
     *
     * @param ui PluginUI instance
     * @return Color int for success state
     */
    public static int getSuccessColor(PluginUI ui) {
        return ui.isDarkTheme() ? 0xFF81C784 : 0xFF4CAF50;  // Green
    }

    /**
     * Get color for warning messages/indicators.
     *
     * @param ui PluginUI instance
     * @return Color int for warning state
     */
    public static int getWarningColor(PluginUI ui) {
        return ui.colorWarning();
    }

    /**
     * Get color for error messages/indicators.
     *
     * @param ui PluginUI instance
     * @return Color int for error state
     */
    public static int getErrorColor(PluginUI ui) {
        return ui.colorError();
    }

    // ==================== Dashboard-Specific Colors ====================

    /**
     * Get color for dashboard header text.
     *
     * @param ui PluginUI instance
     * @return Color int for headers
     */
    public static int getDashboardHeaderColor(PluginUI ui) {
        return ui.colorText();
    }

    /**
     * Get background color for dashboard sections.
     *
     * @param ui PluginUI instance
     * @return Color int for section backgrounds
     */
    public static int getDashboardSectionBackground(PluginUI ui) {
        return ui.isDarkTheme() ? 0xFF1E1E1E : 0xFFFFFFFF;
    }

    /**
     * Get color for dashboard provider status emoji/icon background.
     *
     * @param ui     PluginUI instance
     * @param status Status type
     * @return Color int with alpha for backgrounds
     */
    public static int getDashboardStatusBackground(PluginUI ui, String status) {
        int baseColor = getStatusColor(ui, status);
        // Add 20% alpha for subtle background
        return (baseColor & 0x00FFFFFF) | 0x33000000;
    }

    // ==================== Helper Methods ====================

    /**
     * Blend two colors together.
     *
     * @param color1 First color
     * @param color2 Second color
     * @param ratio  Blend ratio (0.0 = all color1, 1.0 = all color2)
     * @return Blended color
     */
    public static int blendColors(int color1, int color2, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));  // Clamp to 0-1

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Calculate relative luminance of a color (for contrast calculations).
     *
     * @param color Color int
     * @return Luminance value (0.0 - 1.0)
     */
    public static float calculateLuminance(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // Convert to linear RGB
        float rLinear = r / 255f;
        float gLinear = g / 255f;
        float bLinear = b / 255f;

        // Apply gamma correction
        rLinear = (rLinear <= 0.03928f) ? rLinear / 12.92f : (float) Math.pow((rLinear + 0.055f) / 1.055f, 2.4f);
        gLinear = (gLinear <= 0.03928f) ? gLinear / 12.92f : (float) Math.pow((gLinear + 0.055f) / 1.055f, 2.4f);
        bLinear = (bLinear <= 0.03928f) ? bLinear / 12.92f : (float) Math.pow((bLinear + 0.055f) / 1.055f, 2.4f);

        // Calculate luminance
        return 0.2126f * rLinear + 0.7152f * gLinear + 0.0722f * bLinear;
    }

    /**
     * Calculate contrast ratio between two colors.
     *
     * @param color1 First color
     * @param color2 Second color
     * @return Contrast ratio (1.0 - 21.0)
     */
    public static float calculateContrastRatio(int color1, int color2) {
        float lum1 = calculateLuminance(color1);
        float lum2 = calculateLuminance(color2);

        float lighter = Math.max(lum1, lum2);
        float darker = Math.min(lum1, lum2);

        return (lighter + 0.05f) / (darker + 0.05f);
    }

    /**
     * Check if text color has sufficient contrast against background.
     * Uses WCAG AA standard (4.5:1 for normal text).
     *
     * @param textColor       Text color
     * @param backgroundColor Background color
     * @return true if contrast is sufficient
     */
    public static boolean hasSufficientContrast(int textColor, int backgroundColor) {
        return calculateContrastRatio(textColor, backgroundColor) >= 4.5f;
    }
}
