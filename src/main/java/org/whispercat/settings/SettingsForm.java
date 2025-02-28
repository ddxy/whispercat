package org.whispercat.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispercat.ConfigManager;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;
import org.whispercat.recording.clients.FasterWhisperModel;
import org.whispercat.recording.clients.FasterWhisperModelsResponse;

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
    private final JLabel whisperServerLabel;
    private final JComboBox<String> whisperServerComboBox;
    private final JPanel whisperSettingsPanel;
    private final JPanel fasterWhispererPanel;
    private final JPanel grokPanel;
    private final JPanel openaiPanel;
    private final JPanel openWebUIPanel;
    private final JPanel liteLLMPanel;
    private final JTextField whisperServerUrlField;
    private final JComboBox<String> fasterWhisperModelComboBox;
    private final JComboBox<String> fasterWhisperLanguageComboBox;
    // Note: The old API Settings fields have been removed and replaced by new ones below.
    // private JTextField openaiApiKeyField;
    // private JTextField grokApiKeyField;
    // private JTextField openwebUIApiKeyField;
    // private JTextField openwebUIApiURLField;
    // private JTextField liteLLMApiKeyField;
    // private JTextField liteLLMApiURLField;

    // New API Settings fields used in the new API Settings ComboBox with CardLayout
    private JTextField openaiApiKeyFieldNew;
    private JTextField grokApiKeyFieldNew;
    private JTextField openwebUIApiKeyFieldNew;
    private JTextField openwebUIApiURLFieldNew;
    private JTextField liteLLMApiKeyFieldNew;
    private JTextField liteLLMApiURLFieldNew;
    private JComboBox<String> apiSettingsComboBox;

    // Fields for ElevenLabs Synthesizer settings
    private JTextField elevenLabsApiKeyField;

    private static final String SERVER_FASTER_WHISPER = "Faster-Whisper";
    private static final String OPEN_WEB_UI = "Open WebUI";
    private static final String LITE_LLM = "LiteLLM";
    private static final String SERVER_GROK = "Grok";
    private static final String SERVER_OPENAI = "OpenAI";

    private final Map<String, List<String>> fastModelLanguages;

    public SettingsForm(ConfigManager configManager) {
        this.configManager = configManager;
        volumeBar = new JProgressBar(0, 100);
        volumeBar.setStringPainted(true);
        volumeBar.setVisible(false);
        stopTestButton = new JButton("Stop Test");
        stopTestButton.setVisible(false);
        stopTestButton.addActionListener(e -> stopAudioTest());
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getResourceAsStream("/fasterwhispermodels.json")) {
            FasterWhisperModelsResponse response = mapper.readValue(is, FasterWhisperModelsResponse.class);
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

        JPanel contentPanel = new JPanel(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(60, 20, 10, 10));
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

        // ===== New API Settings: Refactored similar to Choose Whisper Server =====
        row++;
        JPanel apiSettingsContainerPanel = new JPanel(new GridBagLayout());
        apiSettingsContainerPanel.setBorder(BorderFactory.createTitledBorder("API Settings"));
        GridBagConstraints apiSettingsGbc = new GridBagConstraints();
        apiSettingsGbc.insets = new Insets(5, 5, 5, 5);
        apiSettingsGbc.fill = GridBagConstraints.HORIZONTAL;
        int apiRow = 0;

        // Add API selection combobox
        JLabel apiSettingsLabel = new JLabel("Choose API:");
        apiSettingsGbc.gridx = 0;
        apiSettingsGbc.gridy = apiRow;
        apiSettingsGbc.gridwidth = 1;
        apiSettingsGbc.weightx = 0;
        apiSettingsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        apiSettingsContainerPanel.add(apiSettingsLabel, apiSettingsGbc);
        String[] apiOptions = {SERVER_OPENAI, "Grok", OPEN_WEB_UI, LITE_LLM};
        apiSettingsComboBox = new JComboBox<>(apiOptions);
        apiSettingsGbc.gridx = 1;
        apiSettingsGbc.gridy = apiRow;
        apiSettingsGbc.gridwidth = 2;
        apiSettingsGbc.weightx = 1.0;
        apiSettingsComboBox.setSelectedIndex(0);
        apiSettingsContainerPanel.add(apiSettingsComboBox, apiSettingsGbc);
        apiRow++;

        // Create a card layout panel for API details
        JPanel apiSettingsCardPanel = new JPanel(new CardLayout());
        GridBagConstraints cardGbc = new GridBagConstraints();
        cardGbc.insets = new Insets(5, 5, 5, 5);
        cardGbc.fill = GridBagConstraints.HORIZONTAL;
        cardGbc.gridx = 0;
        cardGbc.gridy = apiRow;
        cardGbc.gridwidth = 3;
        cardGbc.weightx = 1.0;
        apiSettingsContainerPanel.add(apiSettingsCardPanel, cardGbc);

        // --- Create panels for each API option ---
        // OpenAI Panel
        JPanel openaiPanelAPI = new JPanel(new GridBagLayout());
        GridBagConstraints openaiGbc = new GridBagConstraints();
        openaiGbc.insets = new Insets(5, 5, 5, 5);
        openaiGbc.fill = GridBagConstraints.HORIZONTAL;
        openaiGbc.gridx = 0;
        openaiGbc.gridy = 0;
        openaiGbc.gridwidth = 1;
        openaiGbc.weightx = 0;
        openaiPanelAPI.add(new JLabel("OpenAI API Key:"), openaiGbc);
        openaiApiKeyFieldNew = new JTextField(20);
        openaiGbc.gridx = 1;
        openaiGbc.gridy = 0;
        openaiGbc.gridwidth = 2;
        openaiGbc.weightx = 1.0;
        openaiPanelAPI.add(openaiApiKeyFieldNew, openaiGbc);

        // Grok Panel
        JPanel grokPanelAPI = new JPanel(new GridBagLayout());
        GridBagConstraints grokGbc = new GridBagConstraints();
        grokGbc.insets = new Insets(5, 5, 5, 5);
        grokGbc.fill = GridBagConstraints.HORIZONTAL;
        grokGbc.gridx = 0;
        grokGbc.gridy = 0;
        grokGbc.gridwidth = 1;
        grokGbc.weightx = 0;
        grokPanelAPI.add(new JLabel("Grok API Key:"), grokGbc);
        grokApiKeyFieldNew = new JTextField(20);
        grokGbc.gridx = 1;
        grokGbc.gridy = 0;
        grokGbc.gridwidth = 2;
        grokGbc.weightx = 1.0;
        grokPanelAPI.add(grokApiKeyFieldNew, grokGbc);

        // Open WebUI Panel
        JPanel openWebUIPanelAPI = new JPanel(new GridBagLayout());
        GridBagConstraints openWebUIGbc = new GridBagConstraints();
        openWebUIGbc.insets = new Insets(5, 5, 5, 5);
        openWebUIGbc.fill = GridBagConstraints.HORIZONTAL;
        openWebUIGbc.gridx = 0;
        openWebUIGbc.gridy = 0;
        openWebUIGbc.gridwidth = 1;
        openWebUIGbc.weightx = 0;
        openWebUIPanelAPI.add(new JLabel("OpenWebUI API Key:"), openWebUIGbc);
        openwebUIApiKeyFieldNew = new JTextField(20);
        openWebUIGbc.gridx = 1;
        openWebUIGbc.gridy = 0;
        openWebUIGbc.gridwidth = 2;
        openWebUIGbc.weightx = 1.0;
        openWebUIPanelAPI.add(openwebUIApiKeyFieldNew, openWebUIGbc);
        openWebUIGbc.gridx = 0;
        openWebUIGbc.gridy = 1;
        openWebUIGbc.gridwidth = 1;
        openWebUIGbc.weightx = 0;
        openWebUIPanelAPI.add(new JLabel("OpenWebUI Server URL:"), openWebUIGbc);
        openwebUIApiURLFieldNew = new JTextField(20);
        openWebUIGbc.gridx = 1;
        openWebUIGbc.gridy = 1;
        openWebUIGbc.gridwidth = 2;
        openWebUIGbc.weightx = 1.0;
        openWebUIPanelAPI.add(openwebUIApiURLFieldNew, openWebUIGbc);

        // LiteLLM Panel
        JPanel liteLLMPanelAPI = new JPanel(new GridBagLayout());
        GridBagConstraints liteLLMGbc = new GridBagConstraints();
        liteLLMGbc.insets = new Insets(5, 5, 5, 5);
        liteLLMGbc.fill = GridBagConstraints.HORIZONTAL;
        liteLLMGbc.gridx = 0;
        liteLLMGbc.gridy = 0;
        liteLLMGbc.gridwidth = 1;
        liteLLMGbc.weightx = 0;
        liteLLMPanelAPI.add(new JLabel("LiteLLM API Key:"), liteLLMGbc);
        liteLLMApiKeyFieldNew = new JTextField(20);
        liteLLMGbc.gridx = 1;
        liteLLMGbc.gridy = 0;
        liteLLMGbc.gridwidth = 2;
        liteLLMGbc.weightx = 1.0;
        liteLLMPanelAPI.add(liteLLMApiKeyFieldNew, liteLLMGbc);
        liteLLMGbc.gridx = 0;
        liteLLMGbc.gridy = 1;
        liteLLMGbc.gridwidth = 1;
        liteLLMGbc.weightx = 0;
        liteLLMPanelAPI.add(new JLabel("LiteLLM Server URL:"), liteLLMGbc);
        liteLLMApiURLFieldNew = new JTextField(20);
        liteLLMGbc.gridx = 1;
        liteLLMGbc.gridy = 1;
        liteLLMGbc.gridwidth = 2;
        liteLLMGbc.weightx = 1.0;
        liteLLMPanelAPI.add(liteLLMApiURLFieldNew, liteLLMGbc);

        // Add the individual API panels to the card layout panel
        apiSettingsCardPanel.add(openaiPanelAPI, SERVER_OPENAI);
        apiSettingsCardPanel.add(grokPanelAPI, "Grok");
        apiSettingsCardPanel.add(openWebUIPanelAPI, OPEN_WEB_UI);
        apiSettingsCardPanel.add(liteLLMPanelAPI, LITE_LLM);

        // Add listener to API settings combobox to switch cards based on selection
        apiSettingsComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                CardLayout cl = (CardLayout) apiSettingsCardPanel.getLayout();
                String selectedApi = (String) apiSettingsComboBox.getSelectedItem();
                cl.show(apiSettingsCardPanel, selectedApi);
            }
        });
        // Set initial card
        CardLayout clApi = (CardLayout) apiSettingsCardPanel.getLayout();
        clApi.show(apiSettingsCardPanel, (String) apiSettingsComboBox.getSelectedItem());

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        contentPanel.add(apiSettingsContainerPanel, gbc);
        row++;

        // ===== Whisper Server Settings =====
        // Row: Whisper Server drop-down selection
        row++;
        whisperServerLabel = new JLabel("Choose Whisper Server:");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        contentPanel.add(whisperServerLabel, gbc);
        String[] whisperServers = {SERVER_OPENAI, SERVER_FASTER_WHISPER, OPEN_WEB_UI, LITE_LLM}; // TODO: Add GROK
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
        // ----- Initialize Faster-Whisperer Panel -----
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

        // ----- Initialize Grok Panel -----
        grokPanel = new JPanel(new GridBagLayout());
        GridBagConstraints grokGbc2 = new GridBagConstraints();
        grokGbc2.insets = new Insets(5, 5, 5, 5);
        grokGbc2.fill = GridBagConstraints.HORIZONTAL;
        int groqRow = 0;
        grokGbc2.gridx = 0;
        grokGbc2.gridy = groqRow;
        grokGbc2.gridwidth = 1;
        grokGbc2.weightx = 0;
        grokGbc2.anchor = GridBagConstraints.EAST;
        grokPanel.add(new JLabel("Whisper API Key:"), grokGbc2);

        // In this example, reuse grokPanelAPI if needed – here we simply show the groq input from API settings.
        groqRow++;
        grokGbc2.gridx = 0;
        grokGbc2.gridy = groqRow;
        grokGbc2.gridwidth = 1;
        grokGbc2.weightx = 0;
        grokGbc2.anchor = GridBagConstraints.EAST;
        grokPanel.add(new JLabel("Model:"), grokGbc2);

        // ----- Initialize OpenAI Panel -----
        openaiPanel = new JPanel(new GridBagLayout());
        GridBagConstraints openaiGbc2 = new GridBagConstraints();
        openaiGbc2.insets = new Insets(5, 5, 5, 5);
        openaiGbc2.fill = GridBagConstraints.HORIZONTAL;
        int openaiRow = 0;
        openaiGbc2.gridx = 0;
        openaiGbc2.gridy = openaiRow;
        openaiGbc2.gridwidth = 1;
        openaiGbc2.weightx = 0;
        openaiGbc2.anchor = GridBagConstraints.EAST;
        JLabel noSettingsLabel = new JLabel("No configuration required at this time :-)");
        openaiPanel.add(noSettingsLabel, openaiGbc2);
        // ----- Initialize Open WebUI Panel -----
        openWebUIPanel = new JPanel(new GridBagLayout());
        GridBagConstraints openWebUIGbc2 = new GridBagConstraints();
        openWebUIGbc2.insets = new Insets(5, 5, 5, 5);
        openWebUIGbc2.fill = GridBagConstraints.HORIZONTAL;
        int openWebUIGbcRow = 0;
        openWebUIGbc2.gridx = 0;
        openWebUIGbc2.gridy = openWebUIGbcRow;
        openWebUIGbc2.gridwidth = 1;
        openWebUIGbc2.weightx = 0;
        openWebUIGbc2.anchor = GridBagConstraints.EAST;
        JLabel openWebUInoSettingsLabel = new JLabel("No configuration required at this time :-)");
        openWebUIPanel.add(openWebUInoSettingsLabel, openWebUIGbc2);
        // ----- Initialize LiteLLM Panel -----
        liteLLMPanel = new JPanel(new GridBagLayout());
        GridBagConstraints liteLLMUIGbc = new GridBagConstraints();
        liteLLMUIGbc.insets = new Insets(5, 5, 5, 5);
        liteLLMUIGbc.fill = GridBagConstraints.HORIZONTAL;
        int liteLLMUIGbcRow = 0;
        liteLLMUIGbc.gridx = 0;
        liteLLMUIGbc.gridy = liteLLMUIGbcRow;
        liteLLMUIGbc.gridwidth = 1;
        liteLLMUIGbc.weightx = 0;
        liteLLMUIGbc.anchor = GridBagConstraints.EAST;
        JLabel liteLLMPanelSettingsLabel = new JLabel("No configuration required at this time :-)");
        liteLLMPanel.add(liteLLMPanelSettingsLabel, liteLLMUIGbc);
        // Add sub-panels to the card layout panel
        whisperSettingsPanel.add(openaiPanel, SERVER_OPENAI);
        whisperSettingsPanel.add(fasterWhispererPanel, SERVER_FASTER_WHISPER);
        whisperSettingsPanel.add(openWebUIPanel, OPEN_WEB_UI);
        whisperSettingsPanel.add(liteLLMPanel, LITE_LLM);
        //whisperSettingsPanel.add(groqPanel, SERVER_GROQ);
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

        // ===== Synthesizers Settings =====
        JPanel synthesizersPanel = new JPanel(new GridBagLayout());
        synthesizersPanel.setBorder(BorderFactory.createTitledBorder("Synthesizers Settings"));
        GridBagConstraints synthGbc = new GridBagConstraints();
        synthGbc.insets = new Insets(5, 5, 5, 5);
        synthGbc.fill = GridBagConstraints.HORIZONTAL;
        int synthRow = 0;
        // 11labs API Key field
        synthGbc.gridx = 0;
        synthGbc.gridy = synthRow;
        synthGbc.gridwidth = 1;
        synthGbc.weightx = 0;
        synthGbc.anchor = GridBagConstraints.EAST;
        synthesizersPanel.add(new JLabel("ElevenLabs API Key:"), synthGbc);
        elevenLabsApiKeyField = new JTextField(20);
        synthGbc.gridx = 1;
        synthGbc.gridy = synthRow;
        synthGbc.gridwidth = 2;
        synthGbc.weightx = 1.0;
        synthGbc.anchor = GridBagConstraints.WEST;
        synthesizersPanel.add(elevenLabsApiKeyField, synthGbc);
        // Add the Synthesizers Settings panel to the main content panel
        row++;  // Increment the row counter for the contentPanel
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        contentPanel.add(synthesizersPanel, gbc);

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
                        .addComponent(contentPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addContainerGap()
        );
    }

    private void updateFasterWhisperLanguages() {
        String selectedModel = (String) fasterWhisperModelComboBox.getSelectedItem();
        List<String> languages = fastModelLanguages.getOrDefault(selectedModel, Arrays.asList(""));
        String previouslySelected = (String) fasterWhisperLanguageComboBox.getSelectedItem();
        fasterWhisperLanguageComboBox.removeAllItems();
        for (String lang : languages) {
            fasterWhisperLanguageComboBox.addItem(lang);
        }
        if (previouslySelected != null && languages.contains(previouslySelected)) {
            fasterWhisperLanguageComboBox.setSelectedItem(previouslySelected);
        } else {
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
        // Load API Settings using the new fields
        String openaiKey = configManager.getProperty("apiKey");
        openaiApiKeyFieldNew.setText(openaiKey != null ? openaiKey : "");
        String grokKey = configManager.getProperty("grokApiKey");
        grokApiKeyFieldNew.setText(grokKey != null ? grokKey : "");
        String openwebUIKey = configManager.getOpenWebUIApiKey();
        openwebUIApiKeyFieldNew.setText(openwebUIKey != null ? openwebUIKey : "");
        String openwebUIUrl = configManager.getOpenWebUIServerUrl();
        openwebUIApiURLFieldNew.setText(openwebUIUrl != null ? openwebUIUrl : "");
        String liteLLMKey = configManager.getLiteLLMApiKey();
        liteLLMApiKeyFieldNew.setText(liteLLMKey != null ? liteLLMKey : "");
        String liteLLMUrl = configManager.getLiteLLMServerUrl();
        liteLLMApiURLFieldNew.setText(liteLLMUrl != null ? liteLLMUrl : "");

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
        // Load Grok settings
        String grokApiKey = configManager.getProperty("grokApiKey");


        // Load ElevenLabs Synthesizer settings
        String elevenLabsApiKey = configManager.getProperty("elevenLabsApiKey");
        elevenLabsApiKeyField.setText(elevenLabsApiKey != null ? elevenLabsApiKey : "");
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

        // Save API Settings from the new API Settings panel
        String openaiKey = openaiApiKeyFieldNew.getText();
        configManager.setProperty("apiKey", openaiKey);
        String grokKey = grokApiKeyFieldNew.getText();
        configManager.setProperty("grokApiKey", grokKey);
        String openwebUIKey = openwebUIApiKeyFieldNew.getText();
        configManager.setOpenWebUIApiKey(openwebUIKey);
        String openwebUIUrl = openwebUIApiURLFieldNew.getText();
        configManager.setOpenWebUIServerUrl(openwebUIUrl);
        String liteLLMKey = liteLLMApiKeyFieldNew.getText();
        configManager.setLiteLLMApiKey(liteLLMKey);
        String liteLLMUrl = liteLLMApiURLFieldNew.getText();
        configManager.setLiteLLMServerUrl(liteLLMUrl);

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

        // Save Grok settings
        String grokApiKey = grokApiKeyFieldNew.getText();
        configManager.setProperty("grokApiKey", grokApiKey);

        // Save ElevenLabs Synthesizer settings
        String elevenLabsApiKey = elevenLabsApiKeyField.getText();
        configManager.setProperty("elevenLabsApiKey", elevenLabsApiKey);

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