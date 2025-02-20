package org.whispercat.recording;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * FasterWhisperClient communicates with the Faster-Whisper transcription API endpoint.
 * It sends all Faster-Whisper parameters along with the audio file.
 */
public class FasterWhisperClient {
    private static final Logger logger = LogManager.getLogger(FasterWhisperClient.class);
    private final ConfigManager configManager;

    public FasterWhisperClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Transcribes the given audio file using the transcription API.
     *
     * @param audioFile the audio file to be transcribed.
     * @return the transcription as returned by the API.
     * @throws IOException if an error occurs during the API request.
     */
    public String transcribe(File audioFile) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Build URL from ConfigManager
            String baseUrl = configManager.getFasterWhisperServerUrl().trim();

            if (!baseUrl.toLowerCase().startsWith("http://") && !baseUrl.toLowerCase().startsWith("https://")) {
                baseUrl = "http://" + baseUrl;
            }

            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            String url = baseUrl + "/v1/audio/transcriptions";
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Accept", "application/json");

            // Build multipart/form-data entity with the file and parameters.
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", audioFile, ContentType.create("audio/wav"), audioFile.getName());
            builder.addTextBody("model", configManager.getFasterWhisperModel());
            if (!configManager.getFasterWhisperLanguage().isEmpty()) {
                builder.addTextBody("language", configManager.getFasterWhisperLanguage());
            }
            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);
            logger.info("Transcribing audio file {} with model {} and language {}", audioFile.getName(), configManager.getFasterWhisperModel(), configManager.getFasterWhisperLanguage());

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseString = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                if (statusCode != 200) {
                    logger.error("Error from transcription API. Status: {} Response: {}", statusCode, responseString);
                    throw new IOException("Error from transcription API: " + responseString);
                }
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(responseString);
                // The API may return a plain string or an object with a "text" field.
                if (jsonNode.isTextual()) {
                    return jsonNode.asText();
                } else {
                    return jsonNode.path("text").asText();
                }
            }
        }
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