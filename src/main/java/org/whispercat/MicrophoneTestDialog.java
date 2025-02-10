package org.whispercat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;

public class MicrophoneTestDialog extends JDialog {
    private static final Logger logger = LogManager.getLogger(MicrophoneTestDialog.class);
    private final JProgressBar volumeBar;
    private final JButton stopButton;
    private final ConfigManager configManager;
    private final String microphoneName;
    private AudioFormat format;
    private TargetDataLine line;
    private TestWorker testWorker;

    public MicrophoneTestDialog(Dialog parent, ConfigManager configManager, String microphoneName) {
        super(parent, "Microphone test", true);
        this.configManager = configManager;
        this.microphoneName = microphoneName;

        setLayout(new BorderLayout());
        volumeBar = new JProgressBar(0, 100);
        volumeBar.setStringPainted(true);
        add(volumeBar, BorderLayout.CENTER);

        stopButton = new JButton("Stop");
        add(stopButton, BorderLayout.SOUTH);

        stopButton.addActionListener(e -> stopTest());

        setSize(300, 100);
        setLocationRelativeTo(parent);

        initializeAudio();
        startTest();
    }

    private void initializeAudio() {
        format = configManager.getAudioFormat();
        try {
            Mixer.Info mixerInfo = getMixerInfoByName(microphoneName);
            if (mixerInfo == null) {
                JOptionPane.showMessageDialog(this, "Mikrofon nicht gefunden.", "Fehler", JOptionPane.ERROR_MESSAGE);
                dispose();
                return;
            }

            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                JOptionPane.showMessageDialog(this, "Audio Line not supported by microphone.", "Error", JOptionPane.ERROR_MESSAGE);
                dispose();
                return;
            }

            line = (TargetDataLine) mixer.getLine(dataLineInfo);
            line.open(format);
            line.start();
        } catch (LineUnavailableException ex) {
            logger.error("Mic Line is not available", ex);
            JOptionPane.showMessageDialog(this, "Mic Line is not available. Choose another input device.", "Error", JOptionPane.ERROR_MESSAGE);
            dispose();
        }
    }

    private Mixer.Info getMixerInfoByName(String name) {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixer : mixers) {
            if (mixer.getName().equals(name)) {
                return mixer;
            }
        }
        return null;
    }

    private void startTest() {
        testWorker = new TestWorker();
        testWorker.execute();
    }

    private void stopTest() {
        if (testWorker != null && !testWorker.isDone()) {
            testWorker.cancel(true);
        }
        if (line != null) {
            line.stop();
            line.close();
        }
        dispose();
    }

    private class TestWorker extends SwingWorker<Void, Integer> {
        @Override
        protected Void doInBackground() {
            byte[] buffer = new byte[1024];
            while (!isCancelled()) {
                int bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    double rms = calculateRMS(buffer, bytesRead);
                    int volume = (int) (rms * 100);
                    publish(volume);
                }
            }
            return null;
        }

        @Override
        protected void process(java.util.List<Integer> chunks) {
            int latestVolume = chunks.get(chunks.size() - 1);
            volumeBar.setValue(latestVolume);
            volumeBar.setString(latestVolume + " %");
        }

        @Override
        protected void done() {
            volumeBar.setValue(0);
        }

        private double calculateRMS(byte[] audioData, int bytesRead) {
            long sum = 0;
            for (int i = 0; i < bytesRead; i += 2) {
                if (i + 1 < bytesRead) {
                    int sample = (audioData[i + 1] << 8) | (audioData[i] & 0xFF);
                    sum += (long) sample * sample;
                }
            }
            double rms = Math.sqrt(sum / (bytesRead / 2));
            return Math.min(rms / 32768.0, 1.0);
        }
    }
}