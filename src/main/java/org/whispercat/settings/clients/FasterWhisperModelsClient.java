package org.whispercat.settings.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;
import org.whispercat.recording.clients.FasterWhisperModel;
import org.whispercat.recording.clients.FasterWhisperModelsResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * FasterWhisperClient communicates with the Faster-Whisper transcription API endpoint.
 * It sends all Faster-Whisper parameters along with the audio file.
 */
public class FasterWhisperModelsClient {
    private static final Logger logger = LogManager.getLogger(FasterWhisperModelsClient.class);
    private final ConfigManager configManager;

    public FasterWhisperModelsClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Retrieves the list of available models from the API.
     *
     * @return a list of FasterWhisperModel objects.
     * @throws IOException if an error occurs during the API request.
     */
    public List<FasterWhisperModel> getModels() throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            String baseUrl = configManager.getFasterWhisperServerUrl().trim();
            if (!baseUrl.toLowerCase().startsWith("http://") && !baseUrl.toLowerCase().startsWith("https://")) {
                baseUrl = "http://" + baseUrl;
            }

            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            // Build URL from ConfigManager
            String url = baseUrl + "/v1/models";
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseString = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                if (statusCode != 200) {
                    logger.error("Error from models API. Status: {} Response: {}", statusCode, responseString);
                    throw new IOException("Error from models API: " + responseString);
                }
                ObjectMapper objectMapper = new ObjectMapper();
                FasterWhisperModelsResponse modelsResponse = objectMapper.readValue(responseString, FasterWhisperModelsResponse.class);
                return modelsResponse.getData();
            }
        }
    }
}