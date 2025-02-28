package org.whispercat.postprocessing;

import java.util.List;
import java.util.Arrays;

/**
 * Provides mapping between provider names and their associated models.
 * Also handles retrieval of a default model from the stored value or the ConfigManager.
 */
public class ProviderModelMapping {

    /**
     * Returns the list of models corresponding to the given provider.
     * For "OpenAI", a static list is returned.
     * For "Open WebUI", the models loaded from the owner are returned.
     *
     * @param provider the provider name.
     * @param owner    the ProcessingStepPanelOwner instance to retrieve dynamic data.
     * @return a List of model names.
     */
    public static List<String> getModels(String provider, ProcessingStepPanelOwner owner) {
        if ("OpenAI".equals(provider)) {
            return Arrays.asList("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo");
        } else if ("Open WebUI".equals(provider)) {
            List<String> models = owner.getOpenWebUIModelNames();
            return models != null ? models : Arrays.asList();
        } else if ("LiteLLM".equals(provider)) {
            List<String> models = owner.liteLLMModelNames();
            return models != null ? models : Arrays.asList();
        }
        return Arrays.asList();
    }

    /**
     * Determines the default model for the given provider.
     * Priority: the storedModel (if available) > the ConfigManager property (key: "defaultModel."+provider) > first available model.
     *
     * @param provider    the provider name.
     * @param owner       the ProcessingStepPanelOwner instance.
     * @param storedModel the previously stored model (may be empty).
     * @return the default model as a String.
     */
    public static String getDefaultModel(String provider, ProcessingStepPanelOwner owner, String storedModel) {
        if (storedModel != null && !storedModel.trim().isEmpty()) {
            return storedModel;
        }
        String key = "defaultModel." + provider;
        String configModel = owner.getConfigManager().getProperty(key);
        if (configModel != null && !configModel.trim().isEmpty()) {
            return configModel;
        }
        List<String> models = getModels(provider, owner);
        if (!models.isEmpty()) {
            return models.get(0);
        }
        return "";
    }
}