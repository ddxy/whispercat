package org.whispercat.postprocessing.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.whispercat.ConfigManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;

/**
 * OpenWebUIClient processes text requests, fetches available models and transcribes audio files
 * using the OpenWebUI API. The API base URL is obtained from the ConfigManager.
 *
 * This class now ignores certificate validation.
 */
public class OpenWebUIProcessClient {

    private final ConfigManager configManager;

    public OpenWebUIProcessClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Creates an HttpClient which ignores SSL certificate validation.
     *
     * @return a CloseableHttpClient instance with an all-trusting SSLContext.
     * @throws IOException if an error occurs while creating the SSL context.
     */
    private CloseableHttpClient createHttpClient() throws IOException {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            return HttpClients.custom().setSSLSocketFactory(csf).build();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Processes the transcript using the provided system prompt, user prompt, and model.
     * This method sends an HTTP POST request to the API and returns the generated text.
     *
     * The JSON payload includes:
     * - "model": the model identifier,
     * - "messages": an array of system and user messages,
     * - "params": an object with the key "system" that also contains the system prompt.
     *
     * @param systemPrompt the system prompt.
     * @param userPrompt   the user prompt.
     * @param model        the model identifier.
     * @return the processed text returned by the API.
     * @throws IOException if an error occurs during the API call.
     */
    public String processText(String systemPrompt, String userPrompt, String model) throws IOException {
        try (CloseableHttpClient httpClient = createHttpClient()) {
            String baseUrl = configManager.getOpenWebUIServerUrl().trim();
            if (!baseUrl.toLowerCase().startsWith("http://") && !baseUrl.toLowerCase().startsWith("https://")) {
                baseUrl = "https://" + baseUrl;
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/api/chat/completions";
            HttpPost httpPost = new HttpPost(url);

            httpPost.setHeader("Authorization", "Bearer " + configManager.getProperty("openWebUIApiKey"));
            httpPost.setHeader("Content-Type", "application/json");

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", model);

            // Build messages array.
            ArrayNode messages = mapper.createArrayNode();

            ObjectNode systemMessage = mapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);

            ObjectNode userMessage = mapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messages.add(userMessage);

            payload.set("messages", messages);

            // Add "params" block.
            ObjectNode paramsNode = mapper.createObjectNode();
            paramsNode.put("system", systemPrompt);
            payload.set("params", paramsNode);

            StringEntity entity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = new String(responseEntity.getContent().readAllBytes(), StandardCharsets.UTF_8);
                if (statusCode != 200) {
                    JsonNode errorNode = mapper.readTree(responseString);
                    String errorMessage = errorNode.path("error").path("message").asText();
                    throw new IOException("Error from OpenWebUI API: " + errorMessage);
                }
                JsonNode jsonResponse = mapper.readTree(responseString);
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
     * Fetches all available models from the OpenWebUI API.
     *
     * @return a ModelsResponse object containing the list of models.
     * @throws IOException if an error occurs during the API call.
     */
    public OpenWebUIModelsResponse fetchModels() throws IOException {
        try (CloseableHttpClient httpClient = createHttpClient()) {

            String baseUrl = configManager.getOpenWebUIServerUrl().trim();
            if (!baseUrl.toLowerCase().startsWith("http://") && !baseUrl.toLowerCase().startsWith("https://")) {
                baseUrl = "https://" + baseUrl;
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/api/models";
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "Bearer " + configManager.getProperty("openWebUIApiKey"));
            httpGet.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = new String(responseEntity.getContent().readAllBytes(), StandardCharsets.UTF_8);
                ObjectMapper mapper = new ObjectMapper();
                if (statusCode != 200) {
                    JsonNode errorNode = mapper.readTree(responseString);
                    String errorMessage = errorNode.path("error").path("message").asText();
                    throw new IOException("Error from OpenWebUI API: " + errorMessage);
                }
                return mapper.readValue(responseString, OpenWebUIModelsResponse.class);
            }
        }
    }
}