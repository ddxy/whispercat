package org.whispercat.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;
import org.whispercat.recording.FasterWhisperModel;
import org.whispercat.recording.FasterWhisperModelsResponse;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class SettingsForm extends JPanel {
    private static final Logger logger = LogManager.getLogger(SettingsForm.class);
    // Existing components
    private final KeyCombinationTextField keyCombinationTextField;
    private final JButton clearKeybindButton;
    private final KeySequenceTextField keySequenceTextField;
    private final JButton clearKeySequenceButton;
    private final JButton saveButton;
    private final JComboBox<String> microphoneComboBox;
    private final JComboBox<Integer> bitrateComboBox;
    private final ConfigManager configManager;
    private final JCheckBox stopSoundSwitch;
    private final JProgressBar volumeBar;
    private final JButton stopTestButton;
    private final JButton testMicrophoneButton;
    private AudioFormat format;
    private TargetDataLine line;
    private TestWorker testWorker;

    // New components for Whispering Server selection and configuration
    private final JLabel whisperServerLabel;
    private final JComboBox<String> whisperServerComboBox;
    private final JPanel whisperSettingsPanel; // Panel that holds server-specific sub-panels (CardLayout)
    private final JPanel fasterWhispererPanel;   // Panel for Faster-Whisperer settings
    private final JPanel groqPanel;              // Panel for Groq settings
    private final JPanel openaiPanel;            // Panel for OpenAI settings

    // Components for Faster-Whisperer settings (in this panel API-Key field removed)
    private final JTextField whisperServerUrlField;
    private final JComboBox<String> fasterWhisperModelComboBox;
    private final JComboBox<String> fasterWhisperLanguageComboBox; // New language selectbox

    // Components for Groq settings
    private final JTextField groqApiKeyField;
    private final JComboBox<String> groqModelComboBox;

    // Components for OpenAI settings (for Whisper)
    private JTextField openaiApiKeyField;

    // Constants for Whispering Server options
    private static final String SERVER_FASTER_WHISPER = "Faster-Whisper";
    private static final String SERVER_GROQ = "Groq";
    private static final String SERVER_OPENAI = "OpenAI";

    // Mapping for Faster-Whisper models to available languages (first option is always empty for "no language")
    private final Map<String, List<String>> fastModelLanguages;

    public SettingsForm(ConfigManager configManager) {
        this.configManager = configManager;

        volumeBar = new JProgressBar(0, 100);
        volumeBar.setStringPainted(true);
        volumeBar.setVisible(false);
        stopTestButton = new JButton("Stop Test");
        stopTestButton.setVisible(false);
        stopTestButton.addActionListener(e -> stopAudioTest());

        // Statt der statischen Definitionen wird jetzt die JSON-Ressource geladen und in das FasterWhisperModelsResponse gewrappt.
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getResourceAsStream("/fasterwhispermodels.json")) {
            FasterWhisperModelsResponse response = mapper.readValue(is, FasterWhisperModelsResponse.class);
            // Erzeuge ein Mapping von Modell-ID zu Liste der Sprachen.
            fastModelLanguages = response.getData()
                    .stream()
                    .collect(Collectors.toMap(
                            FasterWhisperModel::getId,
                            model -> {
                                List<String> sortedLangs = new ArrayList<>(model.getLanguage());
                                Collections.sort(sortedLangs);
                                List<String> langs = new ArrayList<>();
                                langs.add("");
                                langs.addAll(sortedLangs);
                                return langs;
                            }
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fasterwhispermodels.json", e);
        }

        // Main content panel using GridBagLayout
        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // Row: Global key combination
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

        // Row: Global key sequence
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

        // Row: Microphone selection
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
        microphoneComboBox.addActionListener(e -> stopAudioTest());
        testMicrophoneButton = new JButton("Test");
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

        // Row: Volume bar and Stop Test button
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

        // Row: Bitrate selection
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

        // Row: Enable Stop Sound
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

        // ===== New Section: Whispering Server Selection and Configuration =====
        // Row: Whisper Server drop-down selection
        row++;
        whisperServerLabel = new JLabel("Choose Whisper Server:");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(whisperServerLabel, gbc);
        String[] whisperServers = {SERVER_OPENAI, SERVER_FASTER_WHISPER}; // TODO: Add GROQ
        whisperServerComboBox = new JComboBox<>(whisperServers);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        contentPanel.add(whisperServerComboBox, gbc);

        // Row: Whisper server settings panel (using CardLayout)
        row++;
        JPanel whisperContainerPanel = new JPanel(new BorderLayout());
        whisperContainerPanel.setBorder(BorderFactory.createTitledBorder("Whisper Server Settings"));
        whisperSettingsPanel = new JPanel(new CardLayout());

        // ----- Initialize Faster-Whisperer Panel (API-Key removal, neue Sprache-Selectbox) -----
        fasterWhispererPanel = new JPanel(new GridBagLayout());
        GridBagConstraints fwGbc = new GridBagConstraints();
        fwGbc.insets = new Insets(5, 5, 5, 5);
        fwGbc.fill = GridBagConstraints.HORIZONTAL;
        int fwRow = 0;
        // Server URL for Faster-Whisperer
        fwGbc.gridx = 0;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 1;
        fwGbc.weightx = 0;
        fwGbc.anchor = GridBagConstraints.EAST;
        fasterWhispererPanel.add(new JLabel("Server URL:"), fwGbc);
        whisperServerUrlField = new JTextField(20);
        fwGbc.gridx = 1;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 2;
        fwGbc.weightx = 1.0;
        fwGbc.anchor = GridBagConstraints.WEST;
        fasterWhispererPanel.add(whisperServerUrlField, fwGbc);
        fwRow++;

        JLabel urlHintLabel = new JLabel("Example: http://localhost:8000");
        urlHintLabel.setFont(new Font("Dialog", Font.ITALIC, 10));
        urlHintLabel.setForeground(Color.GRAY);
        fwGbc.gridx = 1;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 2;
        fwGbc.anchor = GridBagConstraints.WEST;
        fasterWhispererPanel.add(urlHintLabel, fwGbc);

        fwRow++;
        // Model selection for Faster-Whisperer
        fwGbc.gridx = 0;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 1;
        fwGbc.weightx = 0;
        fwGbc.anchor = GridBagConstraints.EAST;
        fasterWhispererPanel.add(new JLabel("Model:"), fwGbc);
        String[] fasterModels = fastModelLanguages.keySet().stream().sorted().toArray(String[]::new);
        fasterWhisperModelComboBox = new JComboBox<>(fasterModels);
        fwGbc.gridx = 1;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 2;
        fwGbc.weightx = 1.0;
        fwGbc.anchor = GridBagConstraints.WEST;
        fasterWhispererPanel.add(fasterWhisperModelComboBox, fwGbc);
        fwRow++;
        // Language selection for Faster-Whisperer
        fwGbc.gridx = 0;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 1;
        fwGbc.weightx = 0;
        fwGbc.anchor = GridBagConstraints.EAST;
        fasterWhispererPanel.add(new JLabel("Language:"), fwGbc);
        fasterWhisperLanguageComboBox = new JComboBox<>();
        fwGbc.gridx = 1;
        fwGbc.gridy = fwRow;
        fwGbc.gridwidth = 2;
        fwGbc.weightx = 1.0;
        fwGbc.anchor = GridBagConstraints.WEST;
        fasterWhispererPanel.add(fasterWhisperLanguageComboBox, fwGbc);

        // Action listener to update available languages whenever the model selection changes.
        fasterWhisperModelComboBox.addActionListener(e -> updateFasterWhisperLanguages());

        // ----- Initialize Groq Panel -----
        groqPanel = new JPanel(new GridBagLayout());
        GridBagConstraints groqGbc = new GridBagConstraints();
        groqGbc.insets = new Insets(5, 5, 5, 5);
        groqGbc.fill = GridBagConstraints.HORIZONTAL;
        int groqRow = 0;
        groqGbc.gridx = 0;
        groqGbc.gridy = groqRow;
        groqGbc.gridwidth = 1;
        groqGbc.weightx = 0;
        groqGbc.anchor = GridBagConstraints.EAST;
        groqPanel.add(new JLabel("Whisper API Key:"), groqGbc);
        groqApiKeyField = new JTextField(20);
        groqGbc.gridx = 1;
        groqGbc.gridy = groqRow;
        groqGbc.gridwidth = 2;
        groqGbc.weightx = 1.0;
        groqGbc.anchor = GridBagConstraints.WEST;
        groqPanel.add(groqApiKeyField, groqGbc);
        groqRow++;
        groqGbc.gridx = 0;
        groqGbc.gridy = groqRow;
        groqGbc.gridwidth = 1;
        groqGbc.weightx = 0;
        groqGbc.anchor = GridBagConstraints.EAST;
        groqPanel.add(new JLabel("Model:"), groqGbc);
        String[] groqModels = {"groq-model-1", "groq-model-2"}; // Dummy models
        groqModelComboBox = new JComboBox<>(groqModels);
        groqGbc.gridx = 1;
        groqGbc.gridy = groqRow;
        groqGbc.gridwidth = 2;
        groqGbc.weightx = 1.0;
        groqGbc.anchor = GridBagConstraints.WEST;
        groqPanel.add(groqModelComboBox, groqGbc);

        // ----- Initialize OpenAI Panel -----
        openaiPanel = new JPanel(new GridBagLayout());
        GridBagConstraints openaiGbc = new GridBagConstraints();
        openaiGbc.insets = new Insets(5, 5, 5, 5);
        openaiGbc.fill = GridBagConstraints.HORIZONTAL;
        int openaiRow = 0;
        openaiGbc.gridx = 0;
        openaiGbc.gridy = openaiRow;
        openaiGbc.gridwidth = 1;
        openaiGbc.weightx = 0;
        openaiGbc.anchor = GridBagConstraints.EAST;
        openaiPanel.add(new JLabel("OpenAI Whisper API Key:"), openaiGbc);
        openaiApiKeyField = new JTextField(20);
        openaiGbc.gridx = 1;
        openaiGbc.gridy = openaiRow;
        openaiGbc.gridwidth = 2;
        openaiGbc.weightx = 1.0;
        openaiGbc.anchor = GridBagConstraints.WEST;
        openaiPanel.add(openaiApiKeyField, openaiGbc);

        // Add sub-panels to the card layout panel
        whisperSettingsPanel.add(openaiPanel, SERVER_OPENAI);
        whisperSettingsPanel.add(fasterWhispererPanel, SERVER_FASTER_WHISPER);
        whisperSettingsPanel.add(groqPanel, SERVER_GROQ);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        whisperContainerPanel.add(whisperSettingsPanel, BorderLayout.CENTER);
        contentPanel.add(whisperContainerPanel, gbc);

        // Add an ItemListener to switch cards based on Whisper Server selection
        whisperServerComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                CardLayout cl = (CardLayout) (whisperSettingsPanel.getLayout());
                String selectedServer = (String) whisperServerComboBox.getSelectedItem();
                cl.show(whisperSettingsPanel, selectedServer);
            }
        });
        // Set initial card based on default selection
        CardLayout cl = (CardLayout) (whisperSettingsPanel.getLayout());
        cl.show(whisperSettingsPanel, (String) whisperServerComboBox.getSelectedItem());

        // Row: Save Button
        row++;
        saveButton = new JButton("Save");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(saveButton);
        saveButton.addActionListener(this::saveSettings);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(buttonPanel, gbc);

        loadSettings();

        // Set the layout for the SettingsForm panel using GroupLayout
        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addComponent(contentPanel)
        );
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(contentPanel)
                        .addContainerGap(237, Short.MAX_VALUE)
        );
    }

    private void updateFasterWhisperLanguages() {
        String selectedModel = (String) fasterWhisperModelComboBox.getSelectedItem();
        List<String> languages = fastModelLanguages.getOrDefault(selectedModel, Arrays.asList(""));
        // Merke bereits ausgewählte Sprache
        String previouslySelected = (String) fasterWhisperLanguageComboBox.getSelectedItem();
        fasterWhisperLanguageComboBox.removeAllItems();
        for (String lang : languages) {
            fasterWhisperLanguageComboBox.addItem(lang);
        }
        // Versuche zuvor ausgewählte Sprache wieder auszuwählen
        if (previouslySelected != null && languages.contains(previouslySelected)) {
            fasterWhisperLanguageComboBox.setSelectedItem(previouslySelected);
        } else {
            // Standard: leer auswählen
            fasterWhisperLanguageComboBox.setSelectedItem("");
        }
    }

    private void startAudioTest(String microphoneName) {
        testMicrophoneButton.setEnabled(false);
        format = configManager.getAudioFormat();
        try {
            Mixer.Info mixerInfo = getMixerInfoByName(microphoneName);
            if (mixerInfo == null) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Microphone not found.");
                return;
            }
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Audio Line not supported. Please select another device.");
                return;
            }
            line = (TargetDataLine) mixer.getLine(dataLineInfo);
            int maxAttempts = 3;
            int attempts = 0;
            boolean opened = false;
            while (attempts < maxAttempts && !opened) {
                try {
                    line.open(format);
                    opened = true;
                } catch (LineUnavailableException ex) {
                    attempts++;
                    if (attempts < maxAttempts) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            logger.error("Interrupted while waiting to retry opening microphone line", ie);
                            return;
                        }
                    } else {
                        logger.error("Mic Line not available after " + maxAttempts + " attempts.", ex);
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                                "Please try again selecting this mic.");
                        return;
                    }
                }
            }
            line.start();
            testWorker = new TestWorker();
            testWorker.execute();
        } catch (LineUnavailableException ex) {
            logger.error("Mic Line is not available. Please select another device.", ex);
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                    "Mic Line is not available. Please select another device.");
        }
    }

    public void stopAudioTest() {
        testMicrophoneButton.setEnabled(true);
        if (testWorker != null && !testWorker.isDone()) {
            testWorker.cancel(true);
        }
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        volumeBar.setVisible(false);
        stopTestButton.setVisible(false);
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
                                    logger.info("Mixer supports format: " + mixerInfo.getName()
                                            + " | Channels: " + channels
                                            + " | Sample Rate: " + sampleRate);
                                    return true;
                                }
                            }
                        }
                    }
                    logger.info("Mixer does not support format: " + mixerInfo.getName());
                    return false;
                })
                .map(i -> i.getName() + " Description: " + i.getDescription())
                .toArray(String[]::new);
    }

    private void loadSettings() {
        // Load key combination and key sequence settings
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
        // OpenAI API Key setting
        String apiKey = configManager.getProperty("apiKey");
        openaiApiKeyField.setText(apiKey != null ? apiKey : "");
        // Microphone and bitrate settings
        String selectedMicrophone = configManager.getProperty("selectedMicrophone");
        microphoneComboBox.setSelectedItem(selectedMicrophone);
        int bitrate = configManager.getAudioBitrate();
        bitrateComboBox.setSelectedItem(bitrate);
        String stopSound = configManager.getProperty("stopSound");
        boolean isStopSoundEnabled = Boolean.parseBoolean(stopSound);
        stopSoundSwitch.setSelected(isStopSoundEnabled);
        // Load Whisper Server selection settings
        String whisperServer = configManager.getProperty("whisperServer");
        if (whisperServer != null && !whisperServer.isEmpty()) {
            whisperServerComboBox.setSelectedItem(whisperServer);
        }
        // Load Faster-Whisperer settings
        String serverUrl = configManager.getFasterWhisperServerUrl();
        whisperServerUrlField.setText(serverUrl != null ? serverUrl : "");
        String fasterModel = configManager.getProperty("fasterWhisperModel");
        if (fasterModel != null) {
            fasterWhisperModelComboBox.setSelectedItem(fasterModel);
        }
        // Update the languages based on the selected model
        updateFasterWhisperLanguages();
        // Load the previously selected language if available
        String selectedLanguage = configManager.getProperty("fasterWhisperLanguage");
        if (selectedLanguage != null && !selectedLanguage.isEmpty()) {
            fasterWhisperLanguageComboBox.setSelectedItem(selectedLanguage);
        } else {
            fasterWhisperLanguageComboBox.setSelectedItem("");
        }
        // Load Groq settings
        String groqApiKey = configManager.getProperty("groqApiKey");
        groqApiKeyField.setText(groqApiKey != null ? groqApiKey : "");
        String groqModel = configManager.getProperty("groqModel");
        if (groqModel != null) {
            groqModelComboBox.setSelectedItem(groqModel);
        }
    }

    private void saveSettings(ActionEvent e) {
        // Save key combination and sequence
        String keyCombinationString = keyCombinationTextField.getKeysDisplayed().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        configManager.setProperty("keyCombination", keyCombinationString);
        String keySequenceString = keySequenceTextField.getKeysDisplayed().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        configManager.setProperty("keySequence", keySequenceString);
        // Save OpenAI Whisper API Key
        String openaiKey = openaiApiKeyField.getText();
        configManager.setProperty("apiKey", openaiKey);
        // Save microphone and bitrate settings
        configManager.setProperty("selectedMicrophone", (String) microphoneComboBox.getSelectedItem());
        int selectedBitrate = (Integer) bitrateComboBox.getSelectedItem();
        configManager.setAudioBitrate(selectedBitrate);
        boolean isStopSoundEnabled = stopSoundSwitch.isSelected();
        configManager.setProperty("stopSound", String.valueOf(isStopSoundEnabled));
        // Save Whisper Server selection and Faster-Whisperer settings
        String selectedWhisperServer = (String) whisperServerComboBox.getSelectedItem();
        configManager.setProperty("whisperServer", selectedWhisperServer);
        String serverUrl = whisperServerUrlField.getText();
        configManager.setProperty("fasterWhisperServerUrl", serverUrl);
        String fwModel = (String) fasterWhisperModelComboBox.getSelectedItem();
        configManager.setProperty("fasterWhisperModel", fwModel);
        String selectedLanguage = (String) fasterWhisperLanguageComboBox.getSelectedItem();
        configManager.setProperty("fasterWhisperLanguage", selectedLanguage);
        // Save Groq settings
        String groqApiKey = groqApiKeyField.getText();
        configManager.setProperty("groqApiKey", groqApiKey);
        String groqModel = (String) groqModelComboBox.getSelectedItem();
        configManager.setProperty("groqModel", groqModel);
        configManager.saveConfig();
        Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS,
                "Settings saved.");
        logger.info("Settings saved: Key shortcuts - {}, Key sequence - {}, Microphone - {}",
                keyCombinationString, keySequenceString, microphoneComboBox.getSelectedItem());
    }

    public KeyCombinationTextField getKeybindTextField() {
        return keyCombinationTextField;
    }

    public KeySequenceTextField getKeySequenceTextField() {
        return keySequenceTextField;
    }
}