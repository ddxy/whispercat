package org.whispercat.recording.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Client class for interacting with the ElevenLabs text-to-speech API.
 * This client takes a text as input and returns an audio file.
 */
public class ElevenLabsClient {

    private static final Logger logger = LogManager.getLogger(ElevenLabsClient.class);

    // Base URL for the ElevenLabs API.
    private static final String BASE_API_URL = "https://api.elevenlabs.io/v1/text-to-speech/";

    private final ConfigManager configManager;

    // The Voice ID to be used (e.g., "RT0Ws4wraMnx4S5vInNL").
    private final String voiceId;

    // The output format for the audio (e.g., "mp3_44100_128").
    private final String outputFormat;

    // ObjectMapper for JSON serialization/deserialization.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a new ElevenLabsClient.
     *
     * @param configManager The configuration manager containing the API key.
     * @param voiceId The Voice ID to be used.
     * @param outputFormat The desired output format.
     */
    public ElevenLabsClient(ConfigManager configManager, String voiceId, String outputFormat) {
        this.configManager = configManager;
        this.voiceId = voiceId;
        this.outputFormat = outputFormat;
    }

    /**
     * Synthesizes speech from the provided text using the ElevenLabs API.
     *
     * @param text The text to be converted to speech.
     * @return A File containing the audio data.
     * @throws IOException if an error occurs during the API call or file writing.
     */
    public File synthesize(String text) throws IOException {
        // Build the API URL with the required query parameter.
        String url = BASE_API_URL + voiceId + "/stream?output_format=" + outputFormat;

        // Create the JSON payload. The model_id is kept consistent with the existing sample.
        String jsonPayload = objectMapper.createObjectNode()
                .put("text", text)
                .put("model_id", "eleven_multilingual_v2")
                .toString();

        if(configManager.getElevenLabsApiKey().isEmpty()) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR, "ElevenLabs API Key is missing");
            return null;
        }

        // Create the HTTP POST request.
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("xi-api-key", configManager.getElevenLabsApiKey());
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

        // Execute the request.
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(httpPost)) {

            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity responseEntity = response.getEntity();
            byte[] responseBytes = responseEntity != null
                    ? responseEntity.getContent().readAllBytes()
                    : new byte[0];

            if (statusCode == 200) {
                // Save the audio bytes to a temporary file.
                File audioFile = File.createTempFile("elevenlabs_output", ".mp3");
                Files.write(audioFile.toPath(), responseBytes);
                logger.info("Audio file saved to: {}", audioFile.getAbsolutePath());
                return audioFile;
            } else {
                // Parse the error message from the response.
                String responseString = new String(responseBytes, StandardCharsets.UTF_8);
                JsonNode jsonNode = objectMapper.readTree(responseString);
                String errorMessage = jsonNode.path("error").path("message").asText();
                logger.error("Error from ElevenLabs API: {}", errorMessage);
                throw new IOException("Error from ElevenLabs API: " + errorMessage);
            }
        }
    }
}