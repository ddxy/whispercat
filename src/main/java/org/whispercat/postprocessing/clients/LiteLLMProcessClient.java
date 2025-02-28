package org.whispercat.postprocessing.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LiteLLMProcessClient {
    private static final Logger logger = LogManager.getLogger(LiteLLMProcessClient.class);

    private final ConfigManager configManager;

    public LiteLLMProcessClient(ConfigManager configManager) {
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
            String baseUrl = configManager.getLiteLLMServerUrl().trim();
            if (!baseUrl.toLowerCase().startsWith("http://") && !baseUrl.toLowerCase().startsWith("https://")) {
                baseUrl = "https://" + baseUrl;
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/chat/completions";
            HttpPost httpPost = new HttpPost(url);

            httpPost.setHeader("Authorization", "Bearer " + configManager.getLiteLLMApiKey());
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

            StringEntity entity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = new String(responseEntity.getContent().readAllBytes(), StandardCharsets.UTF_8);
                if (statusCode != 200) {
                    JsonNode errorNode = mapper.readTree(responseString);
                    String errorMessage = errorNode.path("error").path("message").asText();
                    throw new IOException("Error from LiteLLM API: " + errorMessage);
                }
                JsonNode jsonResponse = mapper.readTree(responseString);
                JsonNode choices = jsonResponse.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).path("message");
                    return messageNode.path("content").asText();
                }
            } catch (IOException e) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR, "Error processing text: " + e.getMessage());
                logger.error("Error processing text: ", e);

            }
        }
        return "";
    }

    /**
     * Fetches all available models from the LiteLLM API.
     *
     * @return a ModelsResponse object containing the list of models.
     * @throws IOException if an error occurs during the API call.
     */
    public LiteLLMModelsResponse fetchModels() throws IOException {
        try (CloseableHttpClient httpClient = createHttpClient()) {

            String baseUrl = configManager.getLiteLLMServerUrl().trim();
            if (!baseUrl.toLowerCase().startsWith("http://") && !baseUrl.toLowerCase().startsWith("https://")) {
                baseUrl = "https://" + baseUrl;
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/models";
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "Bearer " + configManager.getLiteLLMApiKey());
            httpGet.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = new String(responseEntity.getContent().readAllBytes(), StandardCharsets.UTF_8);
                logger.info("Response from LiteLLM API: " + responseString);
                ObjectMapper mapper = new ObjectMapper();
                if (statusCode != 200) {
                    throw new IOException("Error from LiteLLM API: " + responseString);
                }
                return mapper.readValue(responseString, LiteLLMModelsResponse.class);
            }
        }
    }
}