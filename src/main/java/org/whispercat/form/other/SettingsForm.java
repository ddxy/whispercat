package org.whispercat.form.other;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.*;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class SettingsForm extends JPanel {
    private static final Logger logger = LogManager.getLogger(SettingsForm.class);
    private final KeyCombinationTextField keyCombinationTextField;
    private final JButton clearKeybindButton;
    private final KeySequenceTextField keySequenceTextField;
    private final JButton clearKeySequenceButton;
    private final JButton saveButton;
    private final JTextField apiKeyField;
    private final JComboBox<String> microphoneComboBox;
    private final JComboBox<Integer> bitrateComboBox;
    private final ConfigManager configManager;
    private final JCheckBox stopSoundSwitch;

    private final JProgressBar volumeBar;
    private final JButton stopTestButton;

    private AudioFormat format;
    private TargetDataLine line;
    private TestWorker testWorker;

    public SettingsForm(ConfigManager configManager) {
        this.configManager = configManager;

        volumeBar = new JProgressBar(0, 100);
        volumeBar.setStringPainted(true);
        volumeBar.setVisible(false);

        stopTestButton = new JButton("Stop Test");
        stopTestButton.setVisible(false);
        stopTestButton.addActionListener(e -> stopAudioTest());

        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Global key combination:"), gbc);

        keyCombinationTextField = new KeyCombinationTextField();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(keyCombinationTextField, gbc);

        clearKeybindButton = new JButton("Delete");
        gbc.gridx = 2;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        contentPanel.add(clearKeybindButton, gbc);

        clearKeybindButton.addActionListener(e -> {
            keyCombinationTextField.setText("");
            keyCombinationTextField.setKeysDisplayed(new HashSet<>());
        });

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Global key sequence:"), gbc);

        keySequenceTextField = new KeySequenceTextField();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(keySequenceTextField, gbc);

        clearKeySequenceButton = new JButton("Delete");
        gbc.gridx = 2;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        contentPanel.add(clearKeySequenceButton, gbc);

        clearKeySequenceButton.addActionListener(e -> {
            keySequenceTextField.setText("");
            keySequenceTextField.setKeysDisplayed(new ArrayList<>());
        });

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("API Key:"), gbc);

        apiKeyField = new JTextField(20);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(apiKeyField, gbc);

        row++;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Microphone:"), gbc);

        microphoneComboBox = new JComboBox<>(getAvailableMicrophones());
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(microphoneComboBox, gbc);

        JButton testMicrophoneButton = new JButton("Test");
        gbc.gridx = 3;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        contentPanel.add(testMicrophoneButton, gbc);

        testMicrophoneButton.addActionListener(e -> {
            String selectedMicrophone = (String) microphoneComboBox.getSelectedItem();
            if (selectedMicrophone != null && !selectedMicrophone.isEmpty()) {
                startAudioTest(selectedMicrophone);
                volumeBar.setVisible(true);
                stopTestButton.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "No Mic selected. Please select Mic.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        row++;

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(volumeBar, gbc);

        gbc.gridx = 3;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        contentPanel.add(stopTestButton, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Bitrate:"), gbc);

        Integer[] bitrates = {16000, 18000, 20000, 22000, 24000, 26000, 28000, 30000, 32000};
        bitrateComboBox = new JComboBox<>(bitrates);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(bitrateComboBox, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(new JLabel("Enable Stop Sound:"), gbc);

        stopSoundSwitch = new JCheckBox();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(stopSoundSwitch, gbc);

        saveButton = new JButton("Save");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(saveButton);
        saveButton.addActionListener(this::saveSettings);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(buttonPanel, gbc);

        loadSettings();

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);

        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(contentPanel)
        );

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(contentPanel)
                        .addContainerGap(237, Short.MAX_VALUE)
        );

    }

    private void startAudioTest(String microphoneName) {
        format = configManager.getAudioFormat();
        try {
            Mixer.Info mixerInfo = getMixerInfoByName(microphoneName);
            if (mixerInfo == null) {
                JOptionPane.showMessageDialog(this, "Mikrofon nicht gefunden.", "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                JOptionPane.showMessageDialog(this, "Audio Line nicht unterstützt.", "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }
            line = (TargetDataLine) mixer.getLine(dataLineInfo);
            line.open(format);
            line.start();

            // Starte den SwingWorker, der den Lautstärkepegel auswertet und in der ProgressBar anzeigt
            testWorker = new TestWorker();
            testWorker.execute();
        } catch (LineUnavailableException ex) {
            logger.error("Mic Line ist nicht verfügbar", ex);
            JOptionPane.showMessageDialog(this, "Mic Line ist nicht verfügbar. Bitte wähle ein anderes Gerät.", "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Beendet den Audio-Test und blendet die Testkomponenten aus.
     */
    private void stopAudioTest() {
        if (testWorker != null && !testWorker.isDone()) {
            testWorker.cancel(true);
        }
        if (line != null) {
            line.stop();
            line.close();
        }
        volumeBar.setVisible(false);
        stopTestButton.setVisible(false);
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
        protected void process(List<Integer> chunks) {
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

    public static String formatKeyCombination(String keyCombination) {
        return Arrays.stream(keyCombination.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .map(NativeKeyEvent::getKeyText)
                .collect(Collectors.joining(" + "));
    }

    public static String formatKeySequence(String keySequence) {
        return Arrays.stream(keySequence.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .map(NativeKeyEvent::getKeyText)
                .collect(Collectors.joining(" + "));
    }

    public String[] getAvailableMicrophones() {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        return Arrays.stream(mixers)
                .filter(mixerInfo -> {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    Line.Info[] targetLineInfos = mixer.getTargetLineInfo();
                    for (Line.Info lineInfo : targetLineInfos) {
                        if (lineInfo instanceof DataLine.Info) {
                            DataLine.Info dataLineInfo = (DataLine.Info) lineInfo;
                            AudioFormat[] supportedFormats = dataLineInfo.getFormats();
                            for (AudioFormat format : supportedFormats) {
                                int channels = format.getChannels();
                                float sampleRate = format.getSampleRate();
                                boolean isChannelValid = (channels == 1 || channels == 2);
                                if (isChannelValid) {
                                    logger.info("Mixer support the format: " + mixerInfo.getName()
                                            + " | Channels: " + channels
                                            + " | Sample Rate: " + sampleRate);
                                    return true;
                                }
                            }
                        }
                    }
                    logger.info("Mixer do not support this format: " + mixerInfo.getName());
                    return false;
                })
                .map(Mixer.Info::getName)
                .toArray(String[]::new);
    }

    private void loadSettings() {
        String keyCombination = configManager.getKeyCombination();
        if (keyCombination == null || keyCombination.isEmpty()) {
            keyCombinationTextField.setText("");
            keyCombinationTextField.setKeysDisplayed(new HashSet<>());
        } else {
            keyCombinationTextField.setText(formatKeyCombination(keyCombination));
            Set<Integer> keySet = Arrays.stream(keyCombination.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
            keyCombinationTextField.setKeysDisplayed(keySet);
        }

        String keySequence = configManager.getProperty("keySequence");
        if (keySequence == null || keySequence.isEmpty()) {
            keySequenceTextField.setText("");
            keySequenceTextField.setKeysDisplayed(new ArrayList<>());
        } else {
            keySequenceTextField.setText(formatKeySequence(keySequence));
            List<Integer> sequenceSet = Arrays.stream(keySequence.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            keySequenceTextField.setKeysDisplayed(sequenceSet);
        }

        String apiKey = configManager.getProperty("apiKey");
        apiKeyField.setText(apiKey);

        String selectedMicrophone = configManager.getProperty("selectedMicrophone");
        microphoneComboBox.setSelectedItem(selectedMicrophone);

        int bitrate = configManager.getAudioBitrate();
        bitrateComboBox.setSelectedItem(bitrate);

        String stopSound = configManager.getProperty("stopSound");
        boolean isStopSoundEnabled = Boolean.parseBoolean(stopSound);
        stopSoundSwitch.setSelected(isStopSoundEnabled);
    }

    private void saveSettings(ActionEvent e) {
        String keyCombinationString = keyCombinationTextField.getKeysDisplayed().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        configManager.setProperty("keyCombination", keyCombinationString);

        String keySequenceString = keySequenceTextField.getKeysDisplayed().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        configManager.setProperty("keySequence", keySequenceString);

        configManager.setProperty("apiKey", apiKeyField.getText());

        configManager.setProperty("selectedMicrophone", (String) microphoneComboBox.getSelectedItem());

        int selectedBitrate = (Integer) bitrateComboBox.getSelectedItem();
        configManager.setAudioBitrate(selectedBitrate);

        boolean isStopSoundEnabled = stopSoundSwitch.isSelected();
        configManager.setProperty("stopSound", String.valueOf(isStopSoundEnabled));

        configManager.saveConfig();
        NotificationManager2.getInstance().showNotification(ToastNotification2.Type.SUCCESS,
                "Settings saved.");
        logger.info("Settings saved: Key shortcuts - {}, Key sequence - {}, API Key - [REDACTED], Microphone - {}",
                keyCombinationString, keySequenceString, microphoneComboBox.getSelectedItem());
    }

    public KeyCombinationTextField getKeybindTextField() {
        return keyCombinationTextField;
    }

    public KeySequenceTextField getKeySequenceTextField() {
        return keySequenceTextField;
    }
}