package bin.mt.plugin.gemini;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.Gravity;

import bin.mt.json.JSONObject;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;
import bin.mt.plugin.api.ui.PluginEditText;
import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.api.ui.PluginView;
import bin.mt.plugin.api.ui.builder.PluginEditTextBuilder;
import bin.mt.plugin.api.ui.dialog.PluginDialog;
import bin.mt.plugin.common.JSONCompat;
import bin.mt.plugin.provider.Provider;
import bin.mt.plugin.provider.Providers;

/**
 * User-defined OpenAI-compatible endpoints.
 *
 * <p>Any service that exposes {@code /chat/completions} works here: Groq,
 * DeepSeek, Together, Mistral, xAI, or a local Ollama or LM Studio. Entries
 * carry no key pattern, because self-hosted servers accept no key at all.
 *
 * <p>Add, edit and delete only — no reordering, no import or export.
 *
 * @author Ilker Binzet
 */
public class CustomProviderPreference implements PluginPreference {

    private static final String HINTS =
            "Known-good base URLs:\n"
            + "  https://api.groq.com/openai/v1\n"
            + "  https://api.deepseek.com/v1\n"
            + "  https://api.together.xyz/v1\n"
            + "  https://api.x.ai/v1\n"
            + "  http://127.0.0.1:11434/v1   (Ollama)\n"
            + "  http://127.0.0.1:1234/v1    (LM Studio)\n\n"
            + "Leave the API key empty for local servers.";

    private PluginContext context;
    private SharedPreferences preferences;

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        this.context = context;
        this.preferences = context.getPreferences();

        builder.addText("🔌 Custom Endpoints").summary("");

        java.util.List<JSONObject> entries = Providers.customEntries(preferences);
        if (entries.isEmpty()) {
            builder.addText("No endpoints yet")
                    .summary("Add one below to use any OpenAI-compatible service");
        } else {
            for (int i = 0; i < entries.size(); i++) {
                final int index = i;
                JSONObject e = entries.get(i);
                String name = JSONCompat.optString(e, "name", "(unnamed)");
                String baseUrl = JSONCompat.optString(e, "baseUrl", "");
                String model = JSONCompat.optString(e, "model", "");
                boolean hasKey = !TextUtils.isEmpty(JSONCompat.optString(e, "apiKey", ""));

                builder.addText(name)
                        .summary(baseUrl
                                + (model.isEmpty() ? "" : "\n" + model)
                                + (hasKey ? "  •  key set" : "  •  no key"))
                        .onClick((pluginUI, item) -> showEditor(pluginUI, index));
            }
        }

        builder.addText("➕ Add Endpoint")
                .summary("Name, base URL, API key and model")
                .onClick((pluginUI, item) -> showEditor(pluginUI, -1));

        builder.addText("ℹ️ Supported Services").summary(HINTS);
    }

    /** index < 0 adds a new entry; otherwise the existing one is edited. */
    private void showEditor(PluginUI pluginUI, int index) {
        java.util.List<JSONObject> entries = Providers.customEntries(preferences);
        boolean isNew = index < 0 || index >= entries.size();
        JSONObject existing = isNew ? null : entries.get(index);

        PluginView view = pluginUI
                .defaultStyle(new PluginUI.StyleWrapper() {
                    @Override
                    protected void handleEditText(PluginUI ui, PluginEditTextBuilder b) {
                        super.handleEditText(ui, b);
                        b.minLines(1).maxLines(2).textSize(13);
                    }
                })
                .buildVerticalLayout()
                .paddingTop(pluginUI.dialogPaddingVertical() / 2)

                .addTextView().text("Display name")
                .addEditBox("name").text(value(existing, "name"))

                .addTextView().text("Base URL").marginTopDp(8)
                .addEditBox("baseUrl").text(value(existing, "baseUrl"))

                .addTextView().text("API key (leave empty for local servers)").marginTopDp(8)
                .addEditBox("apiKey").text(value(existing, "apiKey"))

                .addTextView().text("Model id").marginTopDp(8)
                .addEditBox("model").text(value(existing, "model"))

                .build();

        PluginDialog.Builder dialog = pluginUI.buildDialog()
                .setTitle(isNew ? "Add Endpoint" : "Edit Endpoint")
                .setView(view)
                .setPositiveButton("{ok}", (d, which) -> {
                    String name = text(view, "name");
                    String baseUrl = text(view, "baseUrl");

                    if (name.isEmpty() || baseUrl.isEmpty()) {
                        context.showToast("Name and base URL are required");
                        return;
                    }

                    JSONObject entry = Providers.newCustomEntry(
                            name, baseUrl, text(view, "apiKey"), text(view, "model"));

                    java.util.List<JSONObject> updated = Providers.customEntries(preferences);
                    if (isNew) {
                        updated.add(entry);
                    } else {
                        updated.set(index, entry);
                    }
                    Providers.saveCustomEntries(preferences, updated);

                    // Show the resolved URL so a wrong base URL is obvious now
                    // rather than as a confusing 404 at translation time.
                    context.showToast("Saved — requests go to "
                            + Provider.chatCompletionsUrl(baseUrl)
                            + ". Re-open settings to refresh.");
                })
                .setNegativeButton("{cancel}", null);

        if (!isNew) {
            dialog.setNeutralButton("{delete}", (d, which) -> {
                java.util.List<JSONObject> updated = Providers.customEntries(preferences);
                if (index < updated.size()) {
                    updated.remove(index);
                    Providers.saveCustomEntries(preferences, updated);
                    context.showToast("Deleted. Re-open settings to refresh.");
                }
            });
        }

        dialog.show();
    }

    private static String value(JSONObject entry, String field) {
        return entry == null ? "" : JSONCompat.optString(entry, field, "");
    }

    private static String text(PluginView view, String id) {
        PluginEditText box = view.requireViewById(id);
        CharSequence s = box.getText();
        return s == null ? "" : s.toString().trim();
    }
}
