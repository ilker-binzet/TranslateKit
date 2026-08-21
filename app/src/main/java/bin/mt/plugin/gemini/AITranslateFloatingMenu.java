package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.io.IOException;

import bin.mt.json.JSONObject;
import bin.mt.plugin.api.drawable.MaterialIcons;
import bin.mt.plugin.api.editor.BaseTextEditorFloatingMenu;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.api.ui.dialog.LoadingDialog;
import bin.mt.plugin.api.util.AsyncTask;
import bin.mt.plugin.common.HttpUtils;
import bin.mt.plugin.common.JSONCompat;
import bin.mt.plugin.provider.Provider;
import bin.mt.plugin.provider.ProviderClient;
import bin.mt.plugin.provider.Providers;

/**
 * AI Translation Floating Menu for Text Editor
 *
 * Shows a floating menu when text is selected in the editor,
 * allowing quick AI-powered translation of the selected text.
 *
 * @author Ilker Binzet
 * @version 1.0.0
 */
public class AITranslateFloatingMenu extends BaseTextEditorFloatingMenu {

    @NonNull
    @Override
    public String name() {
        return getContext().getString("{floating_menu_translate}");
    }

    @NonNull
    @Override
    public Drawable icon() {
        return MaterialIcons.get("translate");
    }

    @Override
    public boolean checkVisible(@NonNull TextEditor editor) {
        // Only show menu when text is selected
        return editor.hasTextSelected();
    }

    @Override
    public void onMenuClick(@NonNull PluginUI pluginUI, @NonNull TextEditor editor) {
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        String selectedText = editor.subText(selStart, selEnd);
        
        if (selectedText == null || selectedText.trim().isEmpty()) {
            pluginUI.showToast(getContext().getString("{error_no_text_selected}"));
            return;
        }
        
        SharedPreferences prefs = getContext().getPreferences();
        String targetLanguage = prefs.getString(GeminiConstants.PREF_DEFAULT_TARGET_LANG, "en");
        String selectedEngine = prefs.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        
        new AsyncTask(getContext()) {
            LoadingDialog loadingDialog;
            String translatedText;
            Exception error;
            
            @Override
            protected void beforeThread() throws Exception {
                String engineName = getEngineDisplayName(prefs, selectedEngine);
                loadingDialog = new LoadingDialog(pluginUI)
                        .setMessage(getContext().getString("{translating_with}") + " " + engineName + "...")
                        .showDelay(200);
            }
            
            @Override
            protected void onThread() throws Exception {
                try {
                    translatedText = performTranslation(selectedText, "auto", targetLanguage, prefs);
                } catch (Exception e) {
                    error = e;
                }
            }
            
            @Override
            protected void afterThread() throws Exception {
                if (error != null) {
                    pluginUI.showToast(getContext().getString("{error_translation_failed}") + ": " + error.getMessage());
                    return;
                }
                
                if (translatedText != null && !translatedText.isEmpty()) {
                    boolean bilingualMode = prefs.getBoolean(GeminiConstants.PREF_BILINGUAL_MODE, GeminiConstants.DEFAULT_BILINGUAL_MODE);
                    String finalText = bilingualMode ? selectedText + "\n" + translatedText : translatedText;
                    editor.replaceText(selStart, selEnd, finalText);
                    pluginUI.showToast(getContext().getString("{translation_complete}"));
                }
            }
            
            @Override
            protected void onException(Exception e) {
                pluginUI.showToast(getContext().getString("{error_translation_failed}") + ": " + e.getMessage());
            }
            
            @Override
            protected void onFinally() {
                if (loadingDialog != null) {
                    loadingDialog.dismiss();
                }
            }
        }.start();
    }
    
    /** Short system directive; the menu translates one selection, not a batch. */
    private static final String SYSTEM_PROMPT =
            "You are a professional translator. Translate text accurately and return only the translation.";

    private String getEngineDisplayName(SharedPreferences prefs, String engine) {
        Provider provider = Providers.byId(prefs, engine);
        return provider != null ? provider.displayName : "AI";
    }

    private String performTranslation(String text, String sourceLang, String targetLang, SharedPreferences prefs) throws IOException {
        Provider provider = Providers.selected(prefs);
        int timeout = readIntPreference(prefs, GeminiConstants.PREF_TIMEOUT, GeminiConstants.DEFAULT_TIMEOUT);

        if (provider.requiresKey() && provider.apiKey.isEmpty()) {
            throw new IOException(provider.displayName + " API key not configured");
        }

        String prompt = buildTranslationPrompt(text, sourceLang, targetLang);
        JSONObject request = ProviderClient.buildRequest(provider, prompt, SYSTEM_PROMPT);
        JSONObject response = HttpUtils.postJson(provider.url(), provider.headers(),
                request.toString(), timeout);

        JSONObject error = ProviderClient.errorOf(response);
        if (error != null) {
            throw new IOException("API Error: "
                    + JSONCompat.optString(error, "message", "Unknown error"));
        }
        return ProviderClient.parseResponse(provider, response);
    }
    
    private String buildTranslationPrompt(String text, String sourceLang, String targetLang) {
        StringBuilder prompt = new StringBuilder();
        
        if ("auto".equals(sourceLang)) {
            prompt.append("Translate the following text to ").append(targetLang).append(".\n");
        } else {
            prompt.append("Translate the following text from ").append(sourceLang)
                  .append(" to ").append(targetLang).append(".\n");
        }
        
        prompt.append("IMPORTANT: Return ONLY the translated text, without any explanations.\n");
        prompt.append("Text to translate:\n");
        prompt.append(text);
        
        return prompt.toString();
    }
    
    private int readIntPreference(SharedPreferences prefs, String key, int defaultValue) {
        try {
            return prefs.getInt(key, defaultValue);
        } catch (ClassCastException ignored) {
            String value = prefs.getString(key, null);
            if (value != null && !value.trim().isEmpty()) {
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return defaultValue;
    }
}
