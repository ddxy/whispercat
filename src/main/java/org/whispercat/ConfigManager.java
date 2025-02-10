package org.whispercat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.*;
import java.util.Properties;

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
}