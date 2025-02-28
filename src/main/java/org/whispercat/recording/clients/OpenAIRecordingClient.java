package org.whispercat.recording.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Client class for interacting with the OpenAI Text-to-Speech API.
 * This client takes a text input and returns an audio file.
 */
public class OpenAIRecordingClient {
    private static final Logger logger = LogManager.getLogger(OpenAIRecordingClient.class);

    // Base URL for the OpenAI TTS API.
    private static final String BASE_API_URL = "https://api.openai.com/v1/audio/speech";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final ConfigManager configManager;
    // ObjectMapper for JSON serialization/deserialization.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a new OpenAIClient.
     *
     * @param configManager The configuration manager containing the API key.
     */
    public OpenAIRecordingClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Processes the transcript using the provided system prompt, user prompt, and model.
     * This method sends a HTTP POST request to the API and returns the generated content.
     *
     * @param systemPrompt the system prompt.
     * @param userPrompt   the user prompt.
     * @param model        the model identifier (e.g., "gpt-4" or "o3-mini").
     * @return the processed text returned by the API.
     * @throws IOException if an error occurs during the API call.
     */
    public String processText(String systemPrompt, String userPrompt, String model) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setHeader("Authorization", "Bearer " + configManager.getOpenWebUIApiKey());
            httpPost.setHeader("Content-Type", "application/json");

            // Build the JSON payload using Jackson.
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", model);

            // Build messages array:
            ArrayNode messages = mapper.createArrayNode();

            // System message.
            ObjectNode systemMessage = mapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);

            // User message. We append the transcript to the user prompt.
            ObjectNode userMessage = mapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messages.add(userMessage);

            payload.set("messages", messages);

            // Convert payload to JSON string.
            StringEntity entity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = new String(responseEntity.getContent().readAllBytes(), StandardCharsets.UTF_8);

                if (statusCode != 200) {
                    // Parse error message from response.
                    JsonNode errorNode = mapper.readTree(responseString);
                    String errorMessage = errorNode.path("error").path("message").asText();
                    throw new IOException("Error from OpenAI API: " + errorMessage);
                }

                // Parse the successful response to get the completion text.
                JsonNode jsonResponse = mapper.readTree(responseString);
                // The response should include a "choices" array with at least one element.
                JsonNode choices = jsonResponse.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).path("message");
                    return messageNode.path("content").asText();
                }
            }
        }
        return "";
    }

    /**
     * Synthesizes speech from the provided text using the OpenAI TTS API.
     *
     * @param text The text to be converted to speech.
     * @return A File containing the audio data.
     * @throws IOException if an error occurs during the API call or file writing.
     */
    public File synthesize(String text, String modelName, String voice) throws IOException {
        // Create the JSON payload.
        JsonNode jsonPayloadNode = objectMapper.createObjectNode()
                .put("model", modelName)
                .put("input", text)
                .put("voice", voice);

        String jsonPayload = objectMapper.writeValueAsString(jsonPayloadNode);

        if (configManager.getApiKey().isEmpty()) {
            Notificationmanager.getInstance().showNotification(
                    ToastNotification.Type.ERROR,
                    "OpenAI API Key is missing"
            );
            return null;
        }

        // Create the HTTP POST request.
        HttpPost httpPost = new HttpPost(BASE_API_URL);
        httpPost.setHeader("Authorization", "Bearer " + configManager.getApiKey());
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
                File audioFile = File.createTempFile("openai_output", ".mp3");
                Files.write(audioFile.toPath(), responseBytes);
                logger.info("Audio file saved to: {}", audioFile.getAbsolutePath());
                Notificationmanager.getInstance().showNotification(
                        ToastNotification.Type.SUCCESS,
                        "Speech synthesis successful. Audio saved."
                );
                return audioFile;
            } else {
                // Parse the error message from the response.
                String responseString = new String(responseBytes, StandardCharsets.UTF_8);
                JsonNode jsonNode = objectMapper.readTree(responseString);
                String errorMessage = jsonNode.path("error").path("message").asText();
                logger.error("Error from OpenAI API: {}", errorMessage);
                Notificationmanager.getInstance().showNotification(
                        ToastNotification.Type.ERROR,
                        "Error from OpenAI API. See logs for details."
                );
                throw new IOException("Error from OpenAI API: " + errorMessage);
            }
        }
    }
}