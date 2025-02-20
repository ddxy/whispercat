package org.whispercat.recording;

import java.util.List;

/**
 * Wrapper class for the response from the /v1/models endpoint.
 */
public class FasterWhisperModelsResponse {
    private List<FasterWhisperModel> data;
    private String object;

    public FasterWhisperModelsResponse() {
    }

    public List<FasterWhisperModel> getData() {
        return data;
    }

    public void setData(List<FasterWhisperModel> data) {
        this.data = data;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }
}