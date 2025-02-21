package org.whispercat.recording.clients;

import java.util.List;

/**
 * Data model representing a model returned by the API.
 */
public class FasterWhisperModel {
    private String id;
    private long created;
    private String object;
    private String owned_by;
    private List<String> language;

    public FasterWhisperModel() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getOwned_by() {
        return owned_by;
    }

    public void setOwned_by(String owned_by) {
        this.owned_by = owned_by;
    }

    public List<String> getLanguage() {
        return language;
    }

    public void setLanguage(List<String> language) {
        this.language = language;
    }
}