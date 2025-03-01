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
        if(audioFile == null) {
            logger.error("Audio file is null");
            return null;
        }
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
            builder.addTextBody("model", "whisper-1");

//            builder.addTextBody("response_format", "srt");

            // currently not supported
//            builder.addTextBody("timestamp_granularities[]", "word", ContentType.TEXT_PLAIN);
//            builder.addTextBody("timestamp_granularities[]", "segment", ContentType.TEXT_PLAIN);
//            builder.addTextBody("response_format", "verbose_json");

            builder.addBinaryBody("file", audioFile, ContentType.create("audio/wav"), audioFile.getName());
            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseString = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                ObjectMapper mapper = new ObjectMapper();
                if (statusCode != 200) {
                    logger.error("Error from LiteLLM API: {}", responseString);
                    throw new IOException("Error from LiteLLM API: " + responseString);
                }
//                return responseString;

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