package org.whispercat;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class SettingsDialog extends JDialog {
    private static final Logger logger = LogManager.getLogger(SettingsDialog.class);
    private final KeyCombinationTextField keyCombinationTextField;
    private final JButton clearKeybindButton;
    private final KeySequenceTextField keySequenceTextField;
    private final JButton clearKeySequenceButton;
    private final JButton saveButton;
    private final JButton cancelButton;
    private final JTextField apiKeyField;
    private final JComboBox<String> microphoneComboBox;
    private final JComboBox<Integer> bitrateComboBox;
    private final ConfigManager configManager;
    private final JCheckBox stopSoundSwitch;

    public SettingsDialog(JFrame parent, ConfigManager configManager) {
        super(parent, "Settings", true);
        this.configManager = configManager;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
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
                MicrophoneTestDialog testDialog = new MicrophoneTestDialog(this, configManager, selectedMicrophone);
                testDialog.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "No Mic selected. Please select Mic.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

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
        cancelButton = new JButton("Cancel");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        saveButton.addActionListener(this::saveSettings);
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(saveButton);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(buttonPanel, gbc);

        getContentPane().add(contentPanel);
        loadSettings();
        pack();
        setLocationRelativeTo(parent);
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
        logger.info("Settings saved: Key shortcuts - {}, Key sequence - {}, API Key - [REDACTED], Microphone - {}",
                keyCombinationString, keySequenceString, microphoneComboBox.getSelectedItem());
        dispose();
    }

    public KeyCombinationTextField getKeybindTextField() {
        return keyCombinationTextField;
    }

    public KeySequenceTextField getKeySequenceTextField() {
        return keySequenceTextField;
    }
}