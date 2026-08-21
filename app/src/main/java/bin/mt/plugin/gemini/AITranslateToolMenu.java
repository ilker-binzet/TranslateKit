package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bin.mt.json.JSONObject;
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
import bin.mt.plugin.common.HttpUtils;
import bin.mt.plugin.common.JSONCompat;
import bin.mt.plugin.provider.Provider;
import bin.mt.plugin.provider.ProviderClient;
import bin.mt.plugin.provider.Providers;

/**
 * AI Translation Tool Menu for Text Editor
 *
 * Adds an "AI Translate" button to the editor toolbar,
 * providing a full translation dialog with engine selection,
 * source/target language options, and live translation preview.
 *
 * @author Ilker Binzet
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
    
    /** Short system directive; the menu translates one selection, not a batch. */
    private static final String SYSTEM_PROMPT =
            "You are a professional translator. Translate text accurately and return only the translation.";

    /**
     * Configured providers, in registry order. Resolved per invocation rather
     * than held in a constant so providers the user adds show up without a
     * restart — and so the list is never limited to the three built-ins.
     */
    private List<Provider> engines() {
        return Providers.all(getContext().getPreferences());
    }

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
        // Clamp: the provider list grows and shrinks as the user adds or
        // removes custom endpoints, so a stored index can outlive its entry.
        int savedEngine = Math.max(0, Math.min(preferences.getInt(KEY_ENGINE, 0), engineNames.size() - 1));
        
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
            List<Provider> available = engines();
            String engine = available.get(Math.max(0, Math.min(engineIdx, available.size() - 1))).id;

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
        List<String> names = new ArrayList<>();
        for (Provider p : engines()) {
            names.add(p.displayName);
        }
        return names;
    }
    
    private void showEngineOptionsMenu(PluginUI pluginUI, PluginView anchor, PluginSpinner engineSpinner) {
        PluginPopupMenu popupMenu = pluginUI.createPopupMenu(anchor);
        PluginMenu menu = popupMenu.getMenu();
        
        List<Provider> available = engines();
        int selection = Math.max(0, Math.min(engineSpinner.getSelection(), available.size() - 1));
        String currentEngine = available.get(selection).id;

        for (Provider p : available) {
            menu.add(p.id, p.displayName + " Settings").setCheckable(true)
                .setChecked(p.id.equals(currentEngine));
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            String itemId = item.getItemId();
            for (int i = 0; i < available.size(); i++) {
                if (available.get(i).id.equals(itemId)) {
                    engineSpinner.setSelection(i);
                    break;
                }
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

                    Provider provider = Providers.byId(prefs, engine);
                    if (provider == null) {
                        provider = Providers.selected(prefs);
                    }
                    if (provider.requiresKey() && provider.apiKey.isEmpty()) {
                        throw new IOException(provider.displayName + " API key not configured");
                    }

                    JSONObject request = ProviderClient.buildRequest(provider, prompt, SYSTEM_PROMPT);
                    JSONObject response = HttpUtils.postJson(provider.url(), provider.headers(),
                            request.toString(), timeout);

                    JSONObject apiError = ProviderClient.errorOf(response);
                    if (apiError != null) {
                        throw new IOException("API Error: "
                                + JSONCompat.optString(apiError, "message", "Unknown error"));
                    }
                    translatedText = ProviderClient.parseResponse(provider, response);
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
