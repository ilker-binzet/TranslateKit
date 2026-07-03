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
 * @author TranslateKit
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

    @NonNull
    @Override
    public String name() {
        return getContext().getString("{tool_menu_ai_translate}");
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
                .addTextView().text(getContext().getString("{source_text}"))
                .addEditBox("inputText").text(hasSelection ? selectedText : "")
                
                // Language selection row
                .addHorizontalLayout().gravity(Gravity.CENTER_VERTICAL).marginTopDp(8).children(layout -> layout
                        .addTextView().text(getContext().getString("{from}"))
                        .addSpinner("sourceLang").items(languageNames).selection(savedSourceLang).width(0).layoutWeight(1).marginLeftDp(4)
                        .addTextView().text("→").marginLeftDp(8).marginRightDp(8)
                        .addTextView().text(getContext().getString("{to}"))
                        .addSpinner("targetLang").items(languageNames.subList(1, languageNames.size())).selection(Math.max(0, savedTargetLang - 1)).width(0).layoutWeight(1).marginLeftDp(4)
                )
                
                // Engine selection row
                .addHorizontalLayout().gravity(Gravity.CENTER_VERTICAL).marginTopDp(8).children(layout -> layout
                        .addTextView().text(getContext().getString("{engine}"))
                        .addSpinner("engine").items(engineNames).selection(savedEngine).width(0).layoutWeight(1).marginLeftDp(4)
                        .addButton("engineOptions").text("⚙").marginLeftDp(4)
                )
                
                // Translate button
                .addButton("translate").text(getContext().getString("{translate}")).widthMatchParent().marginTopDp(12)
                
                // Output text
                .addTextView().text(getContext().getString("{translated_text}")).marginTopDp(12)
                .addEditBox("outputText");
        
        // Add replace button if text was selected
        if (hasSelection) {
            builder.addButton("replace").text(getContext().getString("{replace_original}")).widthMatchParent().enable(false).marginTopDp(8);
        }
        
        PluginView view = builder.build();
        
        PluginDialog dialog = pluginUI.buildDialog()
                .setTitle(name())
                .setView(view)
                .setPositiveButton(getContext().getString("{close}"), null)
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
                pluginUI.showToast(getContext().getString("{error_no_text}"));
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
                    boolean bilingualMode = preferences.getBoolean(GeminiConstants.PREF_BILINGUAL_MODE, GeminiConstants.DEFAULT_BILINGUAL_MODE);
                    String finalText = bilingualMode ? selectedText + "\n" + translation : translation;
                    editor.replaceText(selStart, selEnd, finalText);
                    dialog.dismiss();
                    pluginUI.showToast(getContext().getString("{text_replaced}"));
                }
            });
        }
    }
    
    private List<String> buildLanguageNames() {
        return Arrays.asList(
            getContext().getString("{lang_auto}"),
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
                        .setMessage(getContext().getString("{translating}") + "...")
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
                    pluginUI.showToast(getContext().getString("{error_translation_failed}") + ": " + error.getMessage());
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

    private String translateWithOpenAI(String prompt, SharedPreferences prefs, int timeout) throws IOException {
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
                    .put("content", "You are a professional translator. Return only the translation."));
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

    private String translateWithClaude(String prompt, SharedPreferences prefs, int timeout) throws IOException {
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
            request.put("system", "You are a professional translator. Return only the translation.");

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
