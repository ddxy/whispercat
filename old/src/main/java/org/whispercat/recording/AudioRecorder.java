package org.whispercat.recording;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class AudioRecorder {
    private static final Logger logger = LogManager.getLogger(AudioRecorder.class);
    private final File wavFile;
    private final ConfigManager configManager;
    private TargetDataLine line;

    public AudioRecorder(File wavFile, ConfigManager configManager) {
        this.wavFile = wavFile;
        this.configManager = configManager;
    }

    public void start() {
        try {
            AudioFormat format = configManager.getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            String selectedMicrophone = configManager.getProperty("selectedMicrophone");
            Mixer.Info selectedMixerInfo = getMixerInfoByName(selectedMicrophone);

            Mixer mixer = AudioSystem.getMixer(selectedMixerInfo);
            if (!mixer.isLineSupported(info)) {
                logger.warn("Line not supported for selected mixer");
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING, "Microphone not supported. This can happen if there were too many recordings in a short time. Please restart the application.");
                return;
            }

            line = (TargetDataLine) mixer.getLine(info);
            line.open(format);
            line.start();

            AudioInputStream ais = new AudioInputStream(line);
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile);
        } catch (LineUnavailableException | IOException ex) {
            logger.error("An error occurred during recording", ex);
        }
    }

    public void stop() {
        if (line != null) {
            logger.info("Stopping Line.");
            line.stop();
            line.close();
            line = null;
            logger.info("Line closed.");
        }
    }

    private Mixer.Info getMixerInfoByName(String name) {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixer : mixers) {
            if (name.startsWith(mixer.getName())) {
                return mixer;
            }
        }
        return null;
    }

    public File getOutputFile() {
        return wavFile;
    }
}