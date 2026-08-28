package org.whispercat.recording.clients;

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
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.whispercat.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;

/**
 * OpenWebUIClient processes text requests, fetches available models and transcribes audio files
 * using the OpenWebUI API. The API base URL is obtained from the ConfigManager.
 *
 * This class now ignores certificate validation.
 */
public class OpenWebUITranscribeClient {

    private final ConfigManager configManager;

    public OpenWebUITranscribeClient(ConfigManager configManager) {
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
        try (CloseableHttpClient httpClient = createHttpClient()) {
            // Build URL from ConfigManager.
            String baseUrl = configManager.getOpenWebUIServerUrl().trim();
            if (!baseUrl.toLowerCase().startsWith("http://") && !baseUrl.toLowerCase().startsWith("https://")) {
                baseUrl = "https://" + baseUrl;
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/api/v1/audio/transcriptions";

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + configManager.getOpenWebUIApiKey());

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
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