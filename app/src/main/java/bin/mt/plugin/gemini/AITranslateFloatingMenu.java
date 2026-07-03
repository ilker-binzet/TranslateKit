package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.io.IOException;

import bin.mt.plugin.api.drawable.MaterialIcons;
import bin.mt.plugin.api.editor.BaseTextEditorFloatingMenu;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.api.ui.dialog.LoadingDialog;
import bin.mt.plugin.api.util.AsyncTask;

/**
 * AI Translation Floating Menu for Text Editor
 * 
 * Shows a floating menu when text is selected in the editor,
 * allowing quick AI-powered translation of the selected text.
 * 
 * @author TranslateKit
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
                String engineName = getEngineDisplayName(selectedEngine);
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
    
    private String getEngineDisplayName(String engine) {
        if (engine == null) return "AI";
        switch (engine) {
            case GeminiConstants.ENGINE_GEMINI:
                return "Gemini";
            case GeminiConstants.ENGINE_OPENAI:
                return "OpenAI";
            case GeminiConstants.ENGINE_CLAUDE:
                return "Claude";
            default:
                return "AI";
        }
    }
    
    private String performTranslation(String text, String sourceLang, String targetLang, SharedPreferences prefs) throws IOException {
        String selectedEngine = prefs.getString(GeminiConstants.PREF_DEFAULT_ENGINE, GeminiConstants.DEFAULT_ENGINE);
        int timeout = readIntPreference(prefs, GeminiConstants.PREF_TIMEOUT, GeminiConstants.DEFAULT_TIMEOUT);
        
        String prompt = buildTranslationPrompt(text, sourceLang, targetLang);
        
        switch (selectedEngine) {
            case GeminiConstants.ENGINE_OPENAI:
                return translateWithOpenAI(prompt, sourceLang, targetLang, prefs, timeout);
            case GeminiConstants.ENGINE_CLAUDE:
                return translateWithClaude(prompt, sourceLang, targetLang, prefs, timeout);
            case GeminiConstants.ENGINE_GEMINI:
            default:
                return translateWithGemini(prompt, prefs, timeout);
        }
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
    
    private String translateWithGemini(String prompt, SharedPreferences prefs, int timeout) throws IOException {
        String apiKey = prefs.getString(GeminiConstants.PREF_API_KEY, "");
        String modelName = prefs.getString(GeminiConstants.PREF_MODEL_NAME, GeminiConstants.DEFAULT_MODEL);
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("Gemini API key not configured");
        }
        
        bin.mt.json.JSONObject request = new bin.mt.json.JSONObject();
        try {
            bin.mt.json.JSONArray contents = new bin.mt.json.JSONArray();
            bin.mt.json.JSONObject content = new bin.mt.json.JSONObject();
            bin.mt.json.JSONArray parts = new bin.mt.json.JSONArray();
            bin.mt.json.JSONObject part = new bin.mt.json.JSONObject();
            part.put("text", prompt);
            parts.add(part);
            content.put("parts", parts);
            contents.add(content);
            request.put("contents", contents);

            bin.mt.json.JSONObject generationConfig = new bin.mt.json.JSONObject();
            generationConfig.put("temperature", 0.1);
            generationConfig.put("maxOutputTokens", 2048);
            request.put("generationConfig", generationConfig);
        } catch (Exception e) {
            throw new IOException("Failed to build request", e);
        }

        String apiUrl = String.format("%s/%s:generateContent?key=%s",
            GeminiConstants.API_BASE_URL, modelName, apiKey);

        bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(apiUrl, null, request.toString());
        return parseGeminiResponse(response);
    }
    
    private String translateWithOpenAI(String prompt, String sourceLang, String targetLang, 
                                       SharedPreferences prefs, int timeout) throws IOException {
        String apiKey = prefs.getString(GeminiConstants.PREF_OPENAI_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_OPENAI_MODEL, GeminiConstants.DEFAULT_OPENAI_MODEL);
        String endpoint = prefs.getString(GeminiConstants.PREF_OPENAI_ENDPOINT, GeminiConstants.DEFAULT_OPENAI_ENDPOINT);
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("OpenAI API key not configured");
        }
        
        bin.mt.json.JSONObject request = new bin.mt.json.JSONObject();
        try {
            request.put("model", model);
            bin.mt.json.JSONArray messages = new bin.mt.json.JSONArray();
            messages.add(new bin.mt.json.JSONObject()
                    .put("role", "system")
                    .put("content", "You are a professional translator. Translate text accurately and return only the translation."));
            messages.add(new bin.mt.json.JSONObject()
                    .put("role", "user")
                    .put("content", prompt));
            request.put("messages", messages);
            request.put("temperature", 0.1);
            request.put("max_tokens", 2048);
        } catch (Exception e) {
            throw new IOException("Failed to build request", e);
        }

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(endpoint, headers, request.toString());
        return parseOpenAIResponse(response);
    }
    
    private String translateWithClaude(String prompt, String sourceLang, String targetLang,
                                       SharedPreferences prefs, int timeout) throws IOException {
        String apiKey = prefs.getString(GeminiConstants.PREF_CLAUDE_API_KEY, "");
        String model = prefs.getString(GeminiConstants.PREF_CLAUDE_MODEL, GeminiConstants.DEFAULT_CLAUDE_MODEL);
        String endpoint = prefs.getString(GeminiConstants.PREF_CLAUDE_ENDPOINT, GeminiConstants.DEFAULT_CLAUDE_ENDPOINT);
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("Claude API key not configured");
        }
        
        bin.mt.json.JSONObject request = new bin.mt.json.JSONObject();
        try {
            request.put("model", model);
            request.put("max_tokens", 2048);
            request.put("system", "You are a professional translator. Translate text accurately and return only the translation.");

            bin.mt.json.JSONArray messages = new bin.mt.json.JSONArray();
            bin.mt.json.JSONObject userMessage = new bin.mt.json.JSONObject();
            userMessage.put("role", "user");
            bin.mt.json.JSONArray content = new bin.mt.json.JSONArray();
            bin.mt.json.JSONObject textBlock = new bin.mt.json.JSONObject();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);
            content.add(textBlock);
            userMessage.put("content", content);
            messages.add(userMessage);
            request.put("messages", messages);
        } catch (Exception e) {
            throw new IOException("Failed to build request", e);
        }

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", GeminiConstants.CLAUDE_API_VERSION);
        bin.mt.json.JSONObject response = bin.mt.plugin.common.HttpUtils.postJson(endpoint, headers, request.toString());
        return parseClaudeResponse(response);
    }

    private String parseGeminiResponse(bin.mt.json.JSONObject json) throws IOException {
        try {
            if (json.contains("error")) {
                bin.mt.json.JSONObject error = json.getJSONObject("error");
                throw new IOException("API Error: " + bin.mt.plugin.common.JSONCompat.optString(error, "message", "Unknown error"));
            }

            bin.mt.json.JSONArray candidates = bin.mt.plugin.common.JSONCompat.optJSONArray(json, "candidates");
            if (candidates == null || bin.mt.plugin.common.JSONCompat.size(candidates) == 0) {
                throw new IOException("No translation returned");
            }

            bin.mt.json.JSONObject candidate = bin.mt.plugin.common.JSONCompat.optJSONObject(candidates, 0);
            if (candidate == null) {
                throw new IOException("Invalid candidate");
            }
            bin.mt.json.JSONObject content = candidate.getJSONObject("content");
            bin.mt.json.JSONArray parts = content.getJSONArray("parts");

            if (bin.mt.plugin.common.JSONCompat.size(parts) == 0) {
                throw new IOException("Empty translation response");
            }

            return bin.mt.plugin.common.JSONCompat.optJSONObject(parts, 0).getString("text").trim();
        } catch (Exception e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    private String parseOpenAIResponse(bin.mt.json.JSONObject response) throws IOException {
        try {
            bin.mt.json.JSONArray choices = bin.mt.plugin.common.JSONCompat.optJSONArray(response, "choices");
            if (choices == null || bin.mt.plugin.common.JSONCompat.size(choices) == 0) {
                throw new IOException("No response from OpenAI");
            }

            bin.mt.json.JSONObject message = bin.mt.plugin.common.JSONCompat.optJSONObject(choices, 0);
            if (message != null) {
                message = bin.mt.plugin.common.JSONCompat.optJSONObject(message, "message");
            }
            if (message == null) {
                throw new IOException("Invalid OpenAI response");
            }

            String translation;
            try {
                translation = message.getString("content").trim();
            } catch (Exception stringFail) {
                bin.mt.json.JSONArray contentArr = bin.mt.plugin.common.JSONCompat.optJSONArray(message, "content");
                if (contentArr == null) {
                    throw new IOException("OpenAI message content missing");
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < bin.mt.plugin.common.JSONCompat.size(contentArr); i++) {
                    bin.mt.json.JSONObject block = bin.mt.plugin.common.JSONCompat.optJSONObject(contentArr, i);
                    if (block != null) {
                        sb.append(bin.mt.plugin.common.JSONCompat.optString(block, "text", ""));
                    }
                }
                translation = sb.toString().trim();
            }
            return translation;
        } catch (Exception e) {
            throw new IOException("Failed to parse OpenAI response", e);
        }
    }

    private String parseClaudeResponse(bin.mt.json.JSONObject response) throws IOException {
        bin.mt.json.JSONArray contentArray = bin.mt.plugin.common.JSONCompat.optJSONArray(response, "content");
        if (contentArray == null || bin.mt.plugin.common.JSONCompat.size(contentArray) == 0) {
            throw new IOException("No response from Claude");
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bin.mt.plugin.common.JSONCompat.size(contentArray); i++) {
            bin.mt.json.JSONObject block = bin.mt.plugin.common.JSONCompat.optJSONObject(contentArray, i);
            if (block != null) {
                String text = bin.mt.plugin.common.JSONCompat.optString(block, "text", "");
                if (!text.isEmpty()) {
                    builder.append(text);
                }
            }
        }

        return builder.toString().trim();
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
