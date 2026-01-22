package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.io.IOException;

import bin.mt.plugin.api.LocalString;
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
 * @author AI Translation Hub
 * @version 1.0.0
 */
public class AITranslateFloatingMenu extends BaseTextEditorFloatingMenu {
    
    private LocalString localString;
    
    @NonNull
    @Override
    public String name() {
        if (localString == null) {
            localString = getContext().getAssetLocalString("GeminiTranslate");
        }
        return localString != null ? localString.get("floating_menu_translate") : "AI Translate";
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
        if (localString == null) {
            localString = getContext().getAssetLocalString("GeminiTranslate");
        }
        
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        String selectedText = editor.subText(selStart, selEnd);
        
        if (selectedText == null || selectedText.trim().isEmpty()) {
            pluginUI.showToast(localString != null ? localString.get("error_no_text_selected") : "No text selected");
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
                        .setMessage(localString != null 
                            ? localString.get("translating_with") + " " + engineName + "..."
                            : "Translating with " + engineName + "...")
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
                    pluginUI.showToast(localString != null 
                        ? localString.get("error_translation_failed") + ": " + error.getMessage()
                        : "Translation failed: " + error.getMessage());
                    return;
                }
                
                if (translatedText != null && !translatedText.isEmpty()) {
                    editor.replaceText(selStart, selEnd, translatedText);
                    pluginUI.showToast(localString != null 
                        ? localString.get("translation_complete")
                        : "Translation complete");
                }
            }
            
            @Override
            protected void onException(Exception e) {
                pluginUI.showToast(localString != null 
                    ? localString.get("error_translation_failed") + ": " + e.getMessage()
                    : "Translation failed: " + e.getMessage());
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
        
        org.json.JSONObject request = new org.json.JSONObject();
        try {
            org.json.JSONArray contents = new org.json.JSONArray();
            org.json.JSONObject content = new org.json.JSONObject();
            org.json.JSONArray parts = new org.json.JSONArray();
            org.json.JSONObject part = new org.json.JSONObject();
            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            request.put("contents", contents);
            
            org.json.JSONObject generationConfig = new org.json.JSONObject();
            generationConfig.put("temperature", 0.1);
            generationConfig.put("maxOutputTokens", 2048);
            request.put("generationConfig", generationConfig);
        } catch (org.json.JSONException e) {
            throw new IOException("Failed to build request", e);
        }
        
        String apiUrl = String.format("%s/%s:generateContent?key=%s",
            GeminiConstants.API_BASE_URL, modelName, apiKey);
        
        GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post(apiUrl);
        httpRequest.setTimeout(timeout);
        httpRequest.jsonBody(request);
        
        org.json.JSONObject response = httpRequest.executeToJson();
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
        
        org.json.JSONObject request = new org.json.JSONObject();
        try {
            request.put("model", model);
            org.json.JSONArray messages = new org.json.JSONArray();
            messages.put(new org.json.JSONObject()
                    .put("role", "system")
                    .put("content", "You are a professional translator. Translate text accurately and return only the translation."));
            messages.put(new org.json.JSONObject()
                    .put("role", "user")
                    .put("content", prompt));
            request.put("messages", messages);
            request.put("temperature", 0.1);
            request.put("max_tokens", 2048);
        } catch (org.json.JSONException e) {
            throw new IOException("Failed to build request", e);
        }
        
        GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post(endpoint);
        httpRequest.header("Authorization", "Bearer " + apiKey);
        httpRequest.setTimeout(timeout);
        httpRequest.jsonBody(request);
        
        org.json.JSONObject response = httpRequest.executeToJson();
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
        
        org.json.JSONObject request = new org.json.JSONObject();
        try {
            request.put("model", model);
            request.put("max_tokens", 1024);
            request.put("system", "You are a professional translator. Translate text accurately and return only the translation.");
            
            org.json.JSONArray messages = new org.json.JSONArray();
            org.json.JSONObject userMessage = new org.json.JSONObject();
            userMessage.put("role", "user");
            org.json.JSONArray content = new org.json.JSONArray();
            org.json.JSONObject textBlock = new org.json.JSONObject();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);
            content.put(textBlock);
            userMessage.put("content", content);
            messages.put(userMessage);
            request.put("messages", messages);
        } catch (org.json.JSONException e) {
            throw new IOException("Failed to build request", e);
        }
        
        GeminiHttpUtils.Request httpRequest = GeminiHttpUtils.post(endpoint);
        httpRequest.header("x-api-key", apiKey);
        httpRequest.header("anthropic-version", GeminiConstants.CLAUDE_API_VERSION);
        httpRequest.setTimeout(timeout);
        httpRequest.jsonBody(request);
        
        org.json.JSONObject response = httpRequest.executeToJson();
        return parseClaudeResponse(response);
    }
    
    private String parseGeminiResponse(org.json.JSONObject json) throws IOException {
        try {
            if (json.has("error")) {
                org.json.JSONObject error = json.getJSONObject("error");
                throw new IOException("API Error: " + error.optString("message", "Unknown error"));
            }
            
            org.json.JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new IOException("No translation returned");
            }
            
            org.json.JSONObject candidate = candidates.getJSONObject(0);
            org.json.JSONObject content = candidate.getJSONObject("content");
            org.json.JSONArray parts = content.getJSONArray("parts");
            
            if (parts.length() == 0) {
                throw new IOException("Empty translation response");
            }
            
            return parts.getJSONObject(0).getString("text").trim();
        } catch (org.json.JSONException e) {
            throw new IOException("Failed to parse response", e);
        }
    }
    
    private String parseOpenAIResponse(org.json.JSONObject response) throws IOException {
        try {
            org.json.JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IOException("No response from OpenAI");
            }
            
            org.json.JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                throw new IOException("Invalid OpenAI response");
            }
            
            return message.optString("content", "").trim();
        } catch (org.json.JSONException e) {
            throw new IOException("Failed to parse OpenAI response", e);
        }
    }
    
    private String parseClaudeResponse(org.json.JSONObject response) throws IOException {
        org.json.JSONArray contentArray = response.optJSONArray("content");
        if (contentArray == null || contentArray.length() == 0) {
            throw new IOException("No response from Claude");
        }
        
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contentArray.length(); i++) {
            org.json.JSONObject block = contentArray.optJSONObject(i);
            if (block != null && block.has("text")) {
                builder.append(block.optString("text"));
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
