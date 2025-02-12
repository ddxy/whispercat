package org.whispercat.recording;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.whispercat.ConfigManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class OpenAIClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final ConfigManager configManager;

    public OpenAIClient(ConfigManager configManager) {
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
            httpPost.setHeader("Authorization", "Bearer " + configManager.getApiKey());
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
}