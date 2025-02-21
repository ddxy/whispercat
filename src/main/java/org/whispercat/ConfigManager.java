package org.whispercat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.postprocessing.PostProcessingData;

import javax.sound.sampled.AudioFormat;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class ConfigManager {
    private static final Logger logger = LogManager.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE_NAME = "config.properties";
    private final Properties properties;

    public ConfigManager() {
        properties = new Properties();
        loadConfig();
    }

    private void loadConfig() {
        File configFile = getConfigFilePath();
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                properties.load(input);
                logger.info("Configuration loaded successfully from {}", configFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to load configuration", e);
            }
        }
    }

    public String getKeyCombination() {
        return properties.getProperty("keyCombination", "");
    }

    public String getKeySequence() {
        return properties.getProperty("keySequence", "");
    }

    public void saveConfig() {
        File configFile = getConfigFilePath();
        try (OutputStream output = new FileOutputStream(configFile)) {
            properties.store(output, null);
            logger.info("Configuration saved successfully to {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
        }
    }

    private File getConfigFilePath() {
        String osName = System.getProperty("os.name").toLowerCase();
        String configDirPath;
        if (osName.contains("win")) {
            configDirPath = System.getenv("APPDATA") + File.separator + "WhisperCat";
        } else {
            configDirPath = System.getProperty("user.home") + File.separator + "WhisperCat" + File.separator + ".config";
            logger.info("Config Path is:" + configDirPath);
        }
        File configDir = new File(configDirPath);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, CONFIG_FILE_NAME);
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public String getConfigDirectory() {
        String userHome = System.getProperty("user.home");
        String configDir;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            configDir = System.getenv("APPDATA") + File.separator + "WhisperCat";
        } else {
            configDir = userHome + File.separator + "WhisperCat" + File.separator + ".config";
        }
        return configDir;
    }

    public AudioFormat getAudioFormat() {
        float sampleRate = this.getAudioBitrate();
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = true;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public int getAudioBitrate() {
        String bitrate = properties.getProperty("audioBitrate", "20000");
        try {
            int parsedBitrate = Integer.parseInt(bitrate);
            if (parsedBitrate >= 16000 && parsedBitrate <= 32000) {
                return parsedBitrate;
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid bitrate format, using default 20000");
        }
        return 20000;
    }

    public void setAudioBitrate(int bitrate) {
        properties.setProperty("audioBitrate", String.valueOf(bitrate));
        saveConfig();
    }

    public CharSequence getApiKey() {
        return properties.getProperty("apiKey");
    }

    public CharSequence getMicrophone() {
        return properties.getProperty("selectedMicrophone");
    }

    public boolean isStopSoundEnabled() {
        return Boolean.parseBoolean(properties.getProperty("stopSound", "true"));
    }


    public void savePostProcessingData(PostProcessingData data) {
        Gson gson = new Gson();
        String json = gson.toJson(data);
        JsonArray array;
        String existing = properties.getProperty("postProcessingData", "");
        if (!existing.trim().isEmpty()) {
            try {
                array = JsonParser.parseString(existing).getAsJsonArray();
            } catch (Exception e) {
                logger.error("Existing postProcessingData is not a valid JSON array, creating a new one", e);
                array = new JsonArray();
            }
        } else {
            array = new JsonArray();
        }

        // Parse the incoming JSON and extract its uuid
        JsonElement newElement;
        try {
            newElement = JsonParser.parseString(json);
        } catch (Exception e) {
            logger.error("Failed to parse provided JSON: " + json, e);
            return;
        }

        String newUuid;
        try {
            newUuid = newElement.getAsJsonObject().get("uuid").getAsString();
        } catch (Exception e) {
            logger.error("Provided JSON does not contain a valid 'uuid' field: " + json, e);
            return;
        }

        boolean replaced = false;
        // Iterate over the array to check if an element with the same uuid exists.
        for (int i = 0; i < array.size(); i++) {
            try {
                JsonElement element = array.get(i);
                String existingUuid = element.getAsJsonObject().get("uuid").getAsString();
                if (newUuid.equals(existingUuid)) {
                    // Replace the element with the new one.
                    array.set(i, newElement);
                    replaced = true;
                    break;
                }
            } catch (Exception e) {
                logger.error("Error while processing existing JSON element", e);
            }
        }

        // If not replaced then add the new element.
        if (!replaced) {
            array.add(newElement);
        }

        // Save the updated JSON array to properties and call saveConfig()
        properties.setProperty("postProcessingData", gson.toJson(array));
        saveConfig();
    }

    /**
     * Returns the list of post processing data (as JSON element strings) from the configuration.
     *
     * @return List&lt;String&gt; containing each JSON element as a string; empty list if none exists.
     */
    public List<PostProcessingData> getPostProcessingDataList() {
        Gson gson = new Gson();
        String existing = properties.getProperty("postProcessingData", "[]");
        if (!existing.trim().isEmpty()) {
            try {
                PostProcessingData[] dataArray = gson.fromJson(existing, PostProcessingData[].class);
                return Arrays.asList(dataArray);
            } catch (Exception e) {
                logger.error("Existing postProcessingData is not a valid JSON array", e);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR, "Failed to load post-processing data");
            }
        }
        ;
        return Collections.emptyList();
    }

    public void deletePostProcessingData(String uuid) {
        String existing = properties.getProperty("postProcessingData", "[]");
        if (!existing.trim().isEmpty()) {
            Gson gson = new Gson();
            PostProcessingData[] dataArray = gson.fromJson(existing, PostProcessingData[].class);
            List<PostProcessingData> collect = Arrays.asList(dataArray).stream().filter(data -> !data.uuid.equals(uuid)).collect(Collectors.toList());
            String json = gson.toJson(collect);
            properties.setProperty("postProcessingData", json);
        }
    }


    public void setPostProcessingOnStartup(boolean b) {
        properties.setProperty("postProcessingOnStartup", String.valueOf(b));
        saveConfig();
    }

    public boolean isPostProcessingOnStartup() {
        return Boolean.parseBoolean(properties.getProperty("postProcessingOnStartup", "false"));
    }

    public String getWhisperServer() {
        return properties.getProperty("whisperServer", "OpenAI");
    }

    public String getFasterWhisperModel() {
        return properties.getProperty("fasterWhisperModel", "");
    }

    public String getFasterWhisperLanguage() {
        return properties.getProperty("fasterWhisperLanguage", "");
    }

    public String getFasterWhisperServerUrl() {
        return properties.getProperty("fasterWhisperServerUrl", "");
    }

    public String getLastUsedPostProcessingUUID() {
        return properties.getProperty("lastUsedPostProcessingUUID", "");
    }

    public void setLastUsedPostProcessingUUID(String uuid) {
        properties.setProperty("lastUsedPostProcessingUUID", uuid);
        saveConfig();
    }

    public boolean isAutoPasteEnabled() {
        return Boolean.parseBoolean(properties.getProperty("autoPaste", "true"));
    }

    public void setAutoPasteEnabled(boolean selected) {
        properties.setProperty("autoPaste", String.valueOf(selected));
        saveConfig();
    }

    // openwebUIApiKey
    public String getOpenWebUIApiKey() {
        return properties.getProperty("openWebUIApiKey", "");
    }

    public void setOpenWebUIApiKey(String apiKey) {
        properties.setProperty("openWebUIApiKey", apiKey);
    }

    public String getOpenWebUIServerUrl() {
        return properties.getProperty("openWebUIServerUrl", "");
    }

    public void setOpenWebUIServerUrl(String url) {
        properties.setProperty("openWebUIServerUrl", url);
    }
}