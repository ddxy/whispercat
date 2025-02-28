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
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
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
public class LiteLLMRecordingClient {
    private static final Logger logger = LogManager.getLogger(LiteLLMRecordingClient.class);

    // Base URL for the OpenAI TTS API.
    private static final String BASE_API_URL = "/v1/audio/speech";
    private static final String API_URL = "/v1/chat/completions";

    private final ConfigManager configManager;
    // ObjectMapper for JSON serialization/deserialization.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a new OpenAIClient.
     *
     * @param configManager The configuration manager containing the API key.
     */
    public LiteLLMRecordingClient(ConfigManager configManager) {
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
        // log liteLLM systemPrmopt userPrompt model
        logger.info("LiteLLM systemPrompt and userPrompt and model: " + systemPrompt + " " + userPrompt + " " + model);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setHeader("Authorization", "Bearer " + configManager.getLiteLLMApiKey());
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
                    throw new IOException("Error from LiteLLM API: " + errorMessage);
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
     * Transcribes the given audio file by sending it as multipart/form-data to the OpenWebUI audio transcriptions endpoint.
     * The base URL is obtained from the ConfigManager.
     *
     * The request must include the Bearer API key and send the audio file in the "file" form field.
     * The response is expected to contain a "text" field which is returned.
     *
     * @param audioFile the audio file (e.g., a .wav file) to be transcribed.
     * @return the transcribed text.
     * @throws IOException if an error occurs during the API call.
     */
    public String transcribeAudio(File audioFile) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Build URL from ConfigManager.
            String baseUrl = configManager.getLiteLLMServerUrl().trim();
            if (!baseUrl.toLowerCase().startsWith("http://") && !baseUrl.toLowerCase().startsWith("https://")) {
                baseUrl = "https://" + baseUrl;
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/v1/audio/transcriptions";

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + configManager.getLiteLLMApiKey());

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addTextBody("model", "whisper");
            builder.addBinaryBody("file", audioFile, ContentType.create("audio/wav"), audioFile.getName());
            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseString = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                ObjectMapper mapper = new ObjectMapper();
                if (statusCode != 200) {
                    throw new IOException("Error from transcription API: " + responseString);
                }
                JsonNode jsonResponse = mapper.readTree(responseString);
                if (jsonResponse.has("text")) {
                    return jsonResponse.path("text").asText();
                } else if (jsonResponse.isTextual()) {
                    return jsonResponse.asText();
                }
            }
        }
        return "";
    }


}