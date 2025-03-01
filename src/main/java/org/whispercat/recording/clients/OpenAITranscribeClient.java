package org.whispercat.recording.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
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

public class OpenAITranscribeClient implements WhisperClient{
    private static final Logger logger = LogManager.getLogger(OpenAITranscribeClient.class);
    private static final String API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private final ConfigManager configManager;

    public OpenAITranscribeClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String transcribe(File audioFile) throws IOException {
        if(audioFile == null) {
            logger.info("Audio file is null");
            return null;
        }
        logger.info("Transcribing audio file: {}", audioFile.getName());

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setHeader("Authorization", "Bearer " + configManager.getApiKey());
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // Add the binary audio file with correct content type
            builder.addBinaryBody("file", audioFile, ContentType.create("audio/wav"), audioFile.getName());
            // Set the model parameter
            builder.addTextBody("model", "whisper-1");

//            builder.addTextBody("response_format", "srt");

            // see https://community.openai.com/t/word-level-timestamps-and-sentence-timestamps-together/666462/5
            // use word segments rather than srt to get better results
            builder.addTextBody("timestamp_granularities[]", "word", ContentType.TEXT_PLAIN);
            builder.addTextBody("timestamp_granularities[]", "segment", ContentType.TEXT_PLAIN);
            builder.addTextBody("response_format", "verbose_json");
            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = new String(responseEntity.getContent().readAllBytes(), StandardCharsets.UTF_8);
                if (statusCode != 200) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(responseString);
                    String errorMessage = jsonNode.path("error").path("message").asText();
                    logger.error("Error from OpenAI API: {}", errorMessage);
                    throw new IOException("Error from OpenAI API: " + errorMessage);
                }
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(responseString);
                // Return the transcription text in SRT format
//                 return jsonNode.path("text").asText();
                return responseString;
            }
        }
    }
}