package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import bin.mt.plugin.api.LocalString;
import bin.mt.plugin.api.drawable.MaterialIcons;
import bin.mt.plugin.api.editor.BaseTextEditorToolMenu;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginButton;
import bin.mt.plugin.api.ui.PluginEditText;
import bin.mt.plugin.api.ui.PluginEditTextWatcher;
import bin.mt.plugin.api.ui.PluginSpinner;
import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.api.ui.PluginView;
import bin.mt.plugin.api.ui.builder.PluginButtonBuilder;
import bin.mt.plugin.api.ui.builder.PluginEditTextBuilder;
import bin.mt.plugin.api.ui.dialog.LoadingDialog;
import bin.mt.plugin.api.ui.dialog.PluginDialog;
import bin.mt.plugin.api.ui.menu.PluginMenu;
import bin.mt.plugin.api.ui.menu.PluginPopupMenu;
import bin.mt.plugin.api.util.AsyncTask;

/**
 * AI Translation Tool Menu for Text Editor
 * 
 * Adds an "AI Translate" button to the editor toolbar,
 * providing a full translation dialog with engine selection,
 * source/target language options, and live translation preview.
 * 
 * @author AI Translation Hub
 * @version 1.0.0
 */
public class AITranslateToolMenu extends BaseTextEditorToolMenu {
    
    private static final String KEY_SOURCE_LANG = "sourceLang";
    private static final String KEY_TARGET_LANG = "targetLang";
    private static final String KEY_ENGINE = "engine";
    
    private static final List<String> LANGUAGES = Arrays.asList(
        "auto", "en", "tr", "de", "fr", "es", "it", "pt", "ru", "ja", "ko", 
        "zh-CN", "zh-TW", "ar", "hi", "nl", "sv", "pl", "uk"
    );
    
    private static final List<String> ENGINES = Arrays.asList(
        GeminiConstants.ENGINE_GEMINI,
        GeminiConstants.ENGINE_OPENAI,
        GeminiConstants.ENGINE_CLAUDE
    );
    
    private LocalString localString;
    
    @NonNull
    @Override
    public String name() {
        if (localString == null) {
            localString = getContext().getAssetLocalString("GeminiTranslate");
        }
        return localString != null ? localString.get("tool_menu_ai_translate") : "AI Translate";
    }

    @NonNull
    @Override
    public Drawable icon() {
        return MaterialIcons.get("g_translate");
    }

    @Override
    public boolean checkVisible(@NonNull TextEditor editor) {
        return true; // Always visible in the toolbar
    }

    @Override
    public void onMenuClick(@NonNull PluginUI pluginUI, @NonNull TextEditor editor) {
        if (localString == null) {
            localString = getContext().getAssetLocalString("GeminiTranslate");
        }
        
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        String selectedText = editor.subText(selStart, selEnd);
        boolean hasSelection = !TextUtils.isEmpty(selectedText);
        
        SharedPreferences preferences = getContext().getPreferences();
        
        // Build language display names
        List<String> languageNames = buildLanguageNames();
        List<String> engineNames = buildEngineNames();
        
        // Get saved preferences
        int savedSourceLang = preferences.getInt(KEY_SOURCE_LANG, 0);
        int savedTargetLang = preferences.getInt(KEY_TARGET_LANG, 1);
        int savedEngine = preferences.getInt(KEY_ENGINE, 0);
        
        // Build the dialog view
        PluginEditTextBuilder builder = pluginUI
                .defaultStyle(new PluginUI.StyleWrapper() {
                    @Override
                    protected void handleEditText(PluginUI pluginUI, PluginEditTextBuilder builder) {
                        super.handleEditText(pluginUI, builder);
                        builder.minLines(4).maxLines(8).textSize(13).softWrap(PluginEditText.SOFT_WRAP_KEEP_WORD);
                    }

                    @Override
                    protected void handleButton(PluginUI pluginUI, PluginButtonBuilder builder) {
                        super.handleButton(pluginUI, builder);
                        builder.style(PluginButton.Style.FILLED);
                    }
                })
                .buildVerticalLayout()
                .paddingTop(pluginUI.dialogPaddingVertical() / 2)
                
                // Source text input
                .addTextView().text(localString != null ? localString.get("source_text") : "Source Text")
                .addEditBox("inputText").text(hasSelection ? selectedText : "")
                
                // Language selection row
                .addHorizontalLayout().gravity(Gravity.CENTER_VERTICAL).marginTopDp(8).children(layout -> layout
                        .addTextView().text(localString != null ? localString.get("from") : "From:")
                        .addSpinner("sourceLang").items(languageNames).selection(savedSourceLang).width(0).layoutWeight(1).marginLeftDp(4)
                        .addTextView().text("→").marginLeftDp(8).marginRightDp(8)
                        .addTextView().text(localString != null ? localString.get("to") : "To:")
                        .addSpinner("targetLang").items(languageNames.subList(1, languageNames.size())).selection(Math.max(0, savedTargetLang - 1)).width(0).layoutWeight(1).marginLeftDp(4)
                )
                
                // Engine selection row
                .addHorizontalLayout().gravity(Gravity.CENTER_VERTICAL).marginTopDp(8).children(layout -> layout
                        .addTextView().text(localString != null ? localString.get("engine") : "Engine:")
                        .addSpinner("engine").items(engineNames).selection(savedEngine).width(0).layoutWeight(1).marginLeftDp(4)
                        .addButton("engineOptions").text("⚙").marginLeftDp(4)
                )
                
                // Translate button
                .addButton("translate").text(localString != null ? localString.get("translate") : "Translate").widthMatchParent().marginTopDp(12)
                
                // Output text
                .addTextView().text(localString != null ? localString.get("translated_text") : "Translated Text").marginTopDp(12)
                .addEditBox("outputText");
        
        // Add replace button if text was selected
        if (hasSelection) {
            builder.addButton("replace").text(localString != null ? localString.get("replace_original") : "Replace Original").widthMatchParent().enable(false).marginTopDp(8);
        }
        
        PluginView view = builder.build();
        
        PluginDialog dialog = pluginUI.buildDialog()
                .setTitle(name())
                .setView(view)
                .setPositiveButton(localString != null ? localString.get("close") : "Close", null)
                .show();
        
        PluginEditText inputText = view.requireViewById("inputText");
        PluginEditText outputText = view.requireViewById("outputText");
        PluginSpinner sourceLangSpinner = view.requireViewById("sourceLang");
        PluginSpinner targetLangSpinner = view.requireViewById("targetLang");
        PluginSpinner engineSpinner = view.requireViewById("engine");
        PluginView translateButton = view.requireViewById("translate");
        
        // Engine options popup
        view.requireViewById("engineOptions").setOnClickListener(button -> {
            showEngineOptionsMenu(pluginUI, button, engineSpinner);
        });
        
        // Translate button click
        translateButton.setOnClickListener(button -> {
            String text = inputText.getText().toString();
            if (TextUtils.isEmpty(text)) {
                pluginUI.showToast(localString != null ? localString.get("error_no_text") : "Please enter text to translate");
                return;
            }
            
            int sourceIdx = sourceLangSpinner.getSelection();
            int targetIdx = targetLangSpinner.getSelection() + 1; // +1 because "auto" is not in target list
            int engineIdx = engineSpinner.getSelection();
            
            // Save preferences
            preferences.edit()
                    .putInt(KEY_SOURCE_LANG, sourceIdx)
                    .putInt(KEY_TARGET_LANG, targetIdx)
                    .putInt(KEY_ENGINE, engineIdx)
                    .apply();
            
            String sourceLang = LANGUAGES.get(sourceIdx);
            String targetLang = LANGUAGES.get(targetIdx);
            String engine = ENGINES.get(engineIdx);
            
            performTranslation(pluginUI, text, sourceLang, targetLang, engine, outputText);
        });
        
        // Enable replace button when output has text
        if (hasSelection) {
            PluginView replaceButton = view.requireViewById("replace");
            outputText.addTextChangedListener(new PluginEditTextWatcher.Simple() {
                @Override
                public void afterTextChanged(PluginEditText editText, Editable s) {
                    replaceButton.setEnabled(!TextUtils.isEmpty(s));
                }
            });
            
            replaceButton.setOnClickListener(button -> {
                String translation = outputText.getText().toString();
                if (!TextUtils.isEmpty(translation)) {
                    editor.replaceText(selStart, selEnd, translation);
                    dialog.dismiss();
                    pluginUI.showToast(localString != null ? localString.get("text_replaced") : "Text replaced");
                }
            });
        }
    }
    
    private List<String> buildLanguageNames() {
        return Arrays.asList(
            localString != null ? localString.get("lang_auto") : "Auto Detect",
            "English", "Türkçe", "Deutsch", "Français", "Español", 
            "Italiano", "Português", "Русский", "日本語", "한국어",
            "简体中文", "繁體中文", "العربية", "हिन्दी", "Nederlands", 
            "Svenska", "Polski", "Українська"
        );
    }
    
    private List<String> buildEngineNames() {
        return Arrays.asList("Gemini", "OpenAI", "Claude");
    }
    
    private void showEngineOptionsMenu(PluginUI pluginUI, PluginView anchor, PluginSpinner engineSpinner) {
        PluginPopupMenu popupMenu = pluginUI.createPopupMenu(anchor);
        PluginMenu menu = popupMenu.getMenu();
        
        SharedPreferences prefs = getContext().getPreferences();
        String currentEngine = ENGINES.get(engineSpinner.getSelection());
        
        // Add engine-specific options
        menu.add("gemini", "Gemini Settings").setCheckable(true)
            .setChecked(GeminiConstants.ENGINE_GEMINI.equals(currentEngine));
        menu.add("openai", "OpenAI Settings").setCheckable(true)
            .setChecked(GeminiConstants.ENGINE_OPENAI.equals(currentEngine));
        menu.add("claude", "Claude Settings").setCheckable(true)
            .setChecked(GeminiConstants.ENGINE_CLAUDE.equals(currentEngine));
        
        popupMenu.setOnMenuItemClickListener(item -> {
            String itemId = item.getItemId();
            switch (itemId) {
                case "gemini":
                    engineSpinner.setSelection(0);
                    break;
                case "openai":
                    engineSpinner.setSelection(1);
                    break;
                case "claude":
                    engineSpinner.setSelection(2);
                    break;
            }
            return true;
        });
        
        popupMenu.show();
    }
    
    private void performTranslation(PluginUI pluginUI, String text, String sourceLang, 
                                    String targetLang, String engine, PluginEditText outputText) {
        new AsyncTask(getContext()) {
            LoadingDialog loadingDialog;
            String translatedText;
            Exception error;
            
            @Override
            protected void beforeThread() throws Exception {
                loadingDialog = new LoadingDialog(pluginUI)
                        .setMessage(localString != null 
                            ? localString.get("translating") + "..."
                            : "Translating...")
                        .showDelay(100);
            }
            
            @Override
            protected void onThread() throws Exception {
                try {
                    SharedPreferences prefs = getContext().getPreferences();
                    int timeout = readIntPreference(prefs, GeminiConstants.PREF_TIMEOUT, GeminiConstants.DEFAULT_TIMEOUT);
                    
                    String prompt = buildTranslationPrompt(text, sourceLang, targetLang);
                    
                    switch (engine) {
                        case GeminiConstants.ENGINE_OPENAI:
                            translatedText = translateWithOpenAI(prompt, prefs, timeout);
                            break;
                        case GeminiConstants.ENGINE_CLAUDE:
                            translatedText = translateWithClaude(prompt, prefs, timeout);
                            break;
                        case GeminiConstants.ENGINE_GEMINI:
                        default:
                            translatedText = translateWithGemini(prompt, prefs, timeout);
                            break;
                    }
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
                
                if (translatedText != null) {
                    outputText.setText(translatedText);
                }
            }
            
            @Override
            protected void onException(Exception e) {
                pluginUI.showToast("Error: " + e.getMessage());
            }
            
            @Override
            protected void onFinally() {
                if (loadingDialog != null) {
                    loadingDialog.dismiss();
                }
            }
        }.start();
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
    
    private String translateWithOpenAI(String prompt, SharedPreferences prefs, int timeout) throws IOException {
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
                    .put("content", "You are a professional translator. Return only the translation."));
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
    
    private String translateWithClaude(String prompt, SharedPreferences prefs, int timeout) throws IOException {
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
            request.put("system", "You are a professional translator. Return only the translation.");
            
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
