package org.whispercat.postprocessing.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.whispercat.ConfigManager;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ElevenLabsVoiceClient {
    private final ConfigManager configManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LogManager.getLogger(ElevenLabsVoiceClient.class);

    public ElevenLabsVoiceClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * A simple data class representing a voice returned by ElevenLabs.
     */
    public static class VoiceData {
        private final String voiceId;
        private final String name;
        private final String previewUrl;

        public VoiceData(String name, String voiceId, String previewUrl) {
            this.name = name;
            this.voiceId = voiceId;
            this.previewUrl = previewUrl;
        }

        public String getVoiceId() {
            return voiceId;
        }

        public String getName() {
            return name;
        }

        public String getPreviewUrl() {
            return previewUrl;
        }

        @Override
        public String toString() {
            // Display string: "VoiceName (VoiceID)"
            return name + " (" + voiceId + ")";
        }
    }

    /**
     * Fetches available voices from the ElevenLabs API.
     *
     * @return a list of VoiceData objects.
     * @throws IOException if an error occurs during the API call.
     */
    public List<VoiceData> fetchVoices() throws IOException {
        String apiKey = configManager.getProperty("elevenLabsApiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("ElevenLabs API key is missing.");
        }
        String url = "https://api.elevenlabs.io/v1/voices";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("xi-api-key", apiKey);
            httpGet.setHeader("Content-Type", "application/json");
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                String responseString = new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8);
                if (statusCode != 200) {
                    JsonNode errorNode = objectMapper.readTree(responseString);
                    String errorMessage = errorNode.path("error").path("message").asText("Unknown error");
                    logger.error("Error fetching voices from ElevenLabs API: {}", errorMessage);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Error fetching voices from ElevenLabs API: " + errorMessage);
                    throw new IOException("Error from ElevenLabs API: " + errorMessage);
                }
                JsonNode root = objectMapper.readTree(responseString);
                List<VoiceData> voices = new ArrayList<>();
                // Assuming the voices are provided in an array node named "voices"
                JsonNode voicesNode = root.path("voices");
                if (voicesNode.isArray()) {
                    for (JsonNode voiceNode : voicesNode) {
                        String voiceId = voiceNode.path("voice_id").asText();
                        String voiceName = voiceNode.path("name").asText();
                        // First try to obtain the preview_url if available.
                        String previewUrl = voiceNode.path("preview_url").asText(null);
                        // If preview_url is not present or empty,
                        // then fall back to the samples array.
                        if (previewUrl == null || previewUrl.trim().isEmpty()) {
                            JsonNode samplesNode = voiceNode.path("samples");
                            if (samplesNode.isArray() && samplesNode.size() > 0) {
                                previewUrl = samplesNode.get(0).path("sample_id").asText("default");
                            } else {
                                previewUrl = "default";
                            }
                        }
                        voices.add(new VoiceData(voiceName, voiceId, previewUrl));
                    }
                }
                return voices;
            }
        }
    }

    /**
     * Fetches the sample audio for the given voice.
     *
     * @param previewUrl the preview URL (sample audio URL).
     * @return the audio as a byte array.
     * @throws IOException if an error occurs during the API call.
     */
    public byte[] fetchPreviewURL(String previewUrl) throws IOException {
        String apiKey = configManager.getProperty("elevenLabsApiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("ElevenLabs API key is missing.");
        }
        // If the preview_url already looks like a URL, use it directly.
        String url = previewUrl;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("xi-api-key", apiKey);
            httpGet.setHeader("Content-Type", "application/json");
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                byte[] audioBytes = entity.getContent().readAllBytes();
                if (statusCode != 200) {
                    String errorMsg = new String(audioBytes, StandardCharsets.UTF_8);
                    logger.error("Error fetching sample audio from ElevenLabs API: {}", errorMsg);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Error fetching sample audio: " + errorMsg);
                    throw new IOException("Error fetching sample audio: " + errorMsg);
                }
                return audioBytes;
            }
        }
    }
}