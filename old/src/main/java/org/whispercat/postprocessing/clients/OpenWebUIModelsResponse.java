package org.whispercat.postprocessing.clients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenWebUIModelsResponse represents the JSON response from the OpenWebUI models API.
 * Only the model names are kept in the property modelNames.
 */
public class OpenWebUIModelsResponse {
    private List<String> modelNames;

    /**
     * Creates an OpenWebUIModelsResponse by extracting only the "name" field
     * from each model in the "data" array.
     *
     * @param data List of model entries from the API response.
     */
    @JsonCreator
    public OpenWebUIModelsResponse(@JsonProperty("data") List<ModelEntry> data) {
        this.modelNames = data.stream()
                .map(ModelEntry::getName)
                .collect(Collectors.toList());
    }

    public List<String> getModelNames() {
        return modelNames;
    }

    public void setModelNames(List<String> modelNames) {
        this.modelNames = modelNames;
    }

    /**
     * ModelEntry represents a single model entry in the "data" array.
     * Only the "name" field is needed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelEntry {
        private String name;

        @JsonCreator
        public ModelEntry(@JsonProperty("name") String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}