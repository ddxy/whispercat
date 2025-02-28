package org.whispercat.postprocessing.clients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LiteLLMModelsResponse represents the JSON response from the LiteLLM models API.
 * Only the model IDs are kept in the property modelIds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiteLLMModelsResponse {

    private List<String> modelIds;

    /**
     * Creates a LiteLLMModelsResponse by extracting only the "id" field
     * from each model in the "data" array.
     *
     * @param data List of model entries from the API response.
     */
    @JsonCreator
    public LiteLLMModelsResponse(@JsonProperty("data") List<ModelEntry> data) {
        this.modelIds = data.stream()
                .map(ModelEntry::getId)
                .collect(Collectors.toList());
    }

    public List<String> getModelIds() {
        return modelIds;
    }

    public void setModelIds(List<String> modelIds) {
        this.modelIds = modelIds;
    }

    /**
     * ModelEntry represents a single model entry in the "data" array.
     * Only the "id" field is required.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelEntry {
        private String id;

        /**
         * Creates a ModelEntry with the provided model id.
         *
         * @param id the model id from the API.
         */
        @JsonCreator
        public ModelEntry(@JsonProperty("id") String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}