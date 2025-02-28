package org.whispercat.postprocessing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import org.whispercat.Notificationmanager;
import org.whispercat.ToastNotification;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.io.ByteArrayInputStream;

/*
 * This class has been extracted from PostProcessingForm and now lives in its own file.
 * It uses the ProcessingStepPanelOwner interface to communicate with its parent container,
 * avoiding excessive circular dependencies.
 */
public class ProcessingStepPanel extends JPanel {
    private final ProcessingStepPanelOwner owner;
    private String storedModel = "";
    private String originalProvider = null; // remembers initially loaded provider for Prompt
    private String storedSynthesizerVoice = null;
    private String storedSynthesizerProvider = ""; // default provider for synthesizer
    private String originalSynthProvider = null; // remembers initially loaded provider for Synthesizer

    private JComboBox<String> typeCombo;
    private JPanel promptPanel;
    private JPanel replacementPanel;
    private JPanel synthesizerPanel;
    private JTextArea systemPromptArea;
    private JTextArea userPromptArea;
    private Font defaultFont = new JTextArea().getFont();
    // Provider and Model Combo; now including Open WebUI as an option.
    private JComboBox<String> providerCombo;
    private JComboBox<String> modelCombo;
    // Synthesizer provider and voice combo.
    private JComboBox<String> synthesizerProviderCombo;
    private JComboBox<String> synthesizerVoiceCombo;

    // Play/Stop button for sample playback.
    private JButton playButton;
    private JTextField textToReplaceField;
    private JTextField replacementTextField;
    private Border defaultTextAreaBorder;
    private Border defaultReplacementFieldBorder;
    private final String SYSTEM_PROMPT_PLACEHOLDER = "Enter system instructions, e.g., 'You are a helpful assistant.'";
    private final String USER_PROMPT_PLACEHOLDER = "Enter a user message template. For example: 'Greetings, {{input}}! Welcome to our service.' You can include the placeholder {{input}} to insert user input (this may be repeated several times).";
    // For playing sample audio.
    private Player audioPlayer;
    private SwingWorker<Void, Void> playbackWorker;
    // Static array for OpenAI voices (predefined)

    // Constructor now requires a ProcessingStepPanelOwner reference to avoid circular dependencies.
    public ProcessingStepPanel(ProcessingStepPanelOwner owner) {
        this.owner = owner;
        setBorder(BorderFactory.createTitledBorder("Processing Step"));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);
        add(Box.createVerticalStrut(10));

        // Top Panel with Processing Type and Remove Button.
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        typePanel.add(new JLabel("Processing Type:"));
        // Added new type "Synthesizer" along with existing types.
        typeCombo = new JComboBox<>(new String[]{"Prompt", "Text Replacement", "Synthesizer"});
        typePanel.add(typeCombo);
        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(typePanel, BorderLayout.WEST);
        JButton removeButton = new JButton();
        // Using FlatSVGIcon for the trash icon.
        Icon trashIcon = new FlatSVGIcon("icon/svg/trash.svg", 16, 16);
        removeButton.setIcon(trashIcon);
        removeButton.setToolTipText("Remove this Processing Step");
        removeButton.addActionListener((ActionEvent e) -> {
            owner.removeProcessingStep(ProcessingStepPanel.this);
        });
        JPanel removePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        removePanel.add(removeButton);
        topPanel.add(removePanel, BorderLayout.EAST);
        add(topPanel);

        // Definition of Provider and Model Panel (for Prompt type)
        JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel providerLabel = new JLabel("Provider:");
        providerPanel.add(providerLabel);
        providerPanel.add(Box.createHorizontalStrut(5));
        providerCombo = new JComboBox<>(new String[]{"OpenAI", "Open WebUI", "LiteLLM"});
        providerPanel.add(providerCombo);
        providerPanel.add(Box.createHorizontalStrut(15));
        JLabel modelLabel = new JLabel("Model:");
        providerPanel.add(modelLabel);
        providerPanel.add(Box.createHorizontalStrut(5));
        modelCombo = new JComboBox<>();
        providerPanel.add(modelCombo);
        providerCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateModelCombo();
            }
        });

        // Prompt Panel (for Prompt type)
        promptPanel = new JPanel();
        promptPanel.setLayout(new BoxLayout(promptPanel, BoxLayout.Y_AXIS));
        promptPanel.add(Box.createVerticalStrut(20));
        promptPanel.add(providerPanel);
        promptPanel.add(Box.createVerticalStrut(10));
        JPanel systemPanel = new JPanel(new BorderLayout());
        systemPanel.setBorder(null);
        promptPanel.add(Box.createVerticalStrut(10));
        JLabel systemLabel = new JLabel("System Prompt:");
        systemPanel.add(systemLabel, BorderLayout.NORTH);
        systemPromptArea = new JTextArea(5, 15);
        systemPromptArea.setLineWrap(true);
        systemPromptArea.setWrapStyleWord(true);
        defaultTextAreaBorder = systemPromptArea.getBorder();
        JScrollPane systemScrollPane = new JScrollPane(systemPromptArea);
        systemScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, systemScrollPane.getPreferredSize().height));
        systemPanel.add(systemScrollPane, BorderLayout.CENTER);
        promptPanel.add(systemPanel);
        promptPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBorder(null);
        promptPanel.add(Box.createVerticalStrut(10));
        JLabel userLabel = new JLabel("User Prompt:");
        userPanel.add(userLabel, BorderLayout.NORTH);
        userPromptArea = new JTextArea(5, 15);
        userPromptArea.setLineWrap(true);
        userPromptArea.setWrapStyleWord(true);
        JScrollPane userScrollPane = new JScrollPane(userPromptArea);
        userScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, userScrollPane.getPreferredSize().height));
        userPanel.add(userScrollPane, BorderLayout.CENTER);
        promptPanel.add(userPanel);
        add(promptPanel);

        // Replacement Panel (for Text Replacement type)
        replacementPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        replacementPanel.add(new JLabel("Text to Replace:"));
        textToReplaceField = new JTextField(10);
        defaultReplacementFieldBorder = textToReplaceField.getBorder();
        replacementPanel.add(textToReplaceField);
        replacementPanel.add(new JLabel("Replacement Text:"));
        replacementTextField = new JTextField(10);
        replacementPanel.add(replacementTextField);
        add(replacementPanel);

        // Synthesizer Panel (for Synthesizer type)
        synthesizerPanel = new JPanel();
        synthesizerPanel.setLayout(new BoxLayout(synthesizerPanel, BoxLayout.Y_AXIS));
        synthesizerPanel.add(Box.createVerticalStrut(20));
        JPanel synthSubPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel synthProviderLabel = new JLabel("Provider:");
        synthSubPanel.add(synthProviderLabel);
        synthSubPanel.add(Box.createHorizontalStrut(5));
        // Now offering both "ElevenLabs" and "OpenAI" for the synthesizer provider.
        synthesizerProviderCombo = new JComboBox<>(new String[]{"ElevenLabs", "OpenAI"});
        synthesizerProviderCombo.setSelectedItem("ElevenLabs");
        synthSubPanel.add(synthesizerProviderCombo);
        synthSubPanel.add(Box.createHorizontalStrut(15));
        JLabel synthVoiceLabel = new JLabel("Voice:");
        synthSubPanel.add(synthVoiceLabel);
        synthSubPanel.add(Box.createHorizontalStrut(5));
        synthesizerVoiceCombo = new JComboBox<>();
        synthesizerVoiceCombo.setPreferredSize(new Dimension(120, synthesizerVoiceCombo.getPreferredSize().height));
        synthSubPanel.add(synthesizerVoiceCombo);
        synthSubPanel.add(Box.createHorizontalStrut(5));
        playButton = new JButton("Play");
        // Add an action listener to the play button (only works for ElevenLabs provider)
        playButton.addActionListener(e -> onPlayButtonClicked());
        synthSubPanel.add(playButton);
        // When the synthesizer provider changes, update voice list accordingly.
        synthesizerProviderCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateSynthesizerVoiceCombo();
                String selectedProvider = (String) synthesizerProviderCombo.getSelectedItem();
                playButton.setVisible("ElevenLabs".equals(selectedProvider));
            }
        });
        synthesizerPanel.add(synthSubPanel);
        add(synthesizerPanel);

        // Update field visibility based on selected type.
        updateFieldsVisibility();
        typeCombo.addActionListener(e -> updateFieldsVisibility());
        // Attach text area forwarder listeners (the method is static)
        PostProcessingForm.attachTextAreaForwarder(systemPromptArea, null);
        PostProcessingForm.attachTextAreaForwarder(userPromptArea, null);
        setPlaceholder(systemPromptArea, SYSTEM_PROMPT_PLACEHOLDER, defaultFont);
        setPlaceholder(userPromptArea, USER_PROMPT_PLACEHOLDER, defaultFont);

        // If no step data is loaded initially, ensure defaults for both Prompt and Synthesizer.
        if (originalProvider == null) {
            originalProvider = (String) providerCombo.getSelectedItem();
            updateModelCombo();
        }
        if (originalSynthProvider == null) {
            originalSynthProvider = (String) synthesizerProviderCombo.getSelectedItem();
            updateSynthesizerVoiceCombo();
        }
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    /**
     * Updates the visibility of the panels depending on the selected processing type.
     */
    private void updateFieldsVisibility() {
        String selection = (String) typeCombo.getSelectedItem();
        if ("Prompt".equals(selection)) {
            promptPanel.setVisible(true);
            replacementPanel.setVisible(false);
            synthesizerPanel.setVisible(false);
        } else if ("Text Replacement".equals(selection)) {
            promptPanel.setVisible(false);
            replacementPanel.setVisible(true);
            synthesizerPanel.setVisible(false);
        } else if ("Synthesizer".equals(selection)) {
            promptPanel.setVisible(false);
            replacementPanel.setVisible(false);
            synthesizerPanel.setVisible(true);
            updateSynthesizerVoiceCombo();
            if ("ElevenLabs".equals(synthesizerProviderCombo.getSelectedItem()) &&
                    (owner.getConfigManager().getProperty("elevenLabsApiKey") == null ||
                            owner.getConfigManager().getProperty("elevenLabsApiKey").trim().isEmpty())) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                        "ElevenLabs API key is missing. Please set it in the settings.");
            }
        }
        revalidate();
        repaint();
    }

    /**
     * Updates the model combo for "Prompt" type based on the selected provider.
     * This implementation uses ProviderModelMapping to retrieve the models.
     * If the current provider equals originalProvider then storedModel is considered.
     */
    public void updateModelCombo() {
        String provider = (String) providerCombo.getSelectedItem();
        String defaultModel;
        if (provider.equals(originalProvider)) {
            defaultModel = ProviderModelMapping.getDefaultModel(provider, owner, storedModel);
        } else {
            defaultModel = ProviderModelMapping.getDefaultModel(provider, owner, "");
        }
        modelCombo.removeAllItems();
        java.util.List<String> models = ProviderModelMapping.getModels(provider, owner);
        if ("Open WebUI".equals(provider) && models.isEmpty()) {
            if (!defaultModel.isEmpty()) {
                modelCombo.addItem(defaultModel);
                modelCombo.setSelectedItem(defaultModel);
            } else {
                modelCombo.addItem("No models loaded");
            }
            return;
        }
        for (String model : models) {
            modelCombo.addItem(model);
        }
        if (models.contains(defaultModel)) {
            modelCombo.setSelectedItem(defaultModel);
        } else {
            if (provider.equals(originalProvider) && !defaultModel.isEmpty()) {
                modelCombo.insertItemAt(defaultModel, 0);
                modelCombo.setSelectedItem(defaultModel);
                if ("Open WebUI".equals(provider)) {
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                            "The previously selected Open WebUI model \"" + defaultModel + "\" is not available anymore.");
                }
            }
        }
    }

    /**
     * Updates the synthesizer voice combo using SynthesizerProviderMapping.
     * If the current synthesizer provider equals originalSynthProvider, storedSynthesizerVoice is taken into account.
     * Otherwise, only the standard voices for the provider are shown.
     */
    public void updateSynthesizerVoiceCombo() {
        String synthProvider = (String) synthesizerProviderCombo.getSelectedItem();
        String defaultVoice;
        if (synthProvider.equals(originalSynthProvider)) {
            defaultVoice = SynthesizerProviderMapping.getDefaultVoice(synthProvider, owner, storedSynthesizerVoice);
        } else {
            defaultVoice = SynthesizerProviderMapping.getDefaultVoice(synthProvider, owner, "");
        }
        synthesizerVoiceCombo.removeAllItems();
        java.util.List<String> voices = SynthesizerProviderMapping.getVoices(synthProvider, owner);

        for (String voice : voices) {
            synthesizerVoiceCombo.addItem(voice);
        }

        boolean voiceFound = false;
        for (int i = 0; i < synthesizerVoiceCombo.getItemCount(); i++) {
            if (synthesizerVoiceCombo.getItemAt(i).equals(defaultVoice)) {
                voiceFound = true;
                break;
            }
        }
        if (voiceFound) {
            synthesizerVoiceCombo.setSelectedItem(defaultVoice);
        } else {
            if (synthProvider.equals(originalSynthProvider) && defaultVoice != null && !defaultVoice.trim().isEmpty()) {
                synthesizerVoiceCombo.insertItemAt(defaultVoice, 0);
                synthesizerVoiceCombo.setSelectedItem(defaultVoice);
            } else if (synthesizerVoiceCombo.getItemCount() > 0) {
                synthesizerVoiceCombo.setSelectedIndex(0);
            }
        }
    }

    /**
     * Returns the currently selected processing type.
     *
     * @return the processing type as a String.
     */
    public String getType() {
        return (String) typeCombo.getSelectedItem();
    }

    /**
     * Returns the currently chosen provider.
     * For Synthesizer steps, the provider is taken from synthesizerProviderCombo.
     *
     * @return the provider as a String.
     */
    public String getProvider() {
        if ("Synthesizer".equals(getType())) {
            return (String) synthesizerProviderCombo.getSelectedItem();
        }
        return (String) providerCombo.getSelectedItem();
    }

    /**
     * Loads data from the provided ProcessingStepData and updates the fields.
     * For both Prompt and Synthesizer, if the voice/model specified does not exist,
     * it is added only during the initial load.
     *
     * @param stepData the data for this processing step.
     */
    public void loadStepData(ProcessingStepData stepData) {
        typeCombo.setSelectedItem(stepData.type);
        if ("Prompt".equals(stepData.type)) {
            storedModel = stepData.model;
            originalProvider = stepData.provider;
            providerCombo.setSelectedItem(stepData.provider);
            updateModelCombo();
            boolean modelFound = false;
            for (int i = 0; i < modelCombo.getItemCount(); i++) {
                if (modelCombo.getItemAt(i).equals(stepData.model)) {
                    modelFound = true;
                    break;
                }
            }
            if (!modelFound && stepData.model != null && !stepData.model.trim().isEmpty()) {
                modelCombo.addItem(stepData.model);
            }
            modelCombo.setSelectedItem(stepData.model);
            if (stepData.systemPrompt != null && !stepData.systemPrompt.trim().isEmpty()) {
                systemPromptArea.setText(stepData.systemPrompt);
                systemPromptArea.setFont(defaultFont);
            } else {
                systemPromptArea.setText(SYSTEM_PROMPT_PLACEHOLDER);
                systemPromptArea.setFont(defaultFont.deriveFont(Font.ITALIC));
            }
            if (stepData.userPrompt != null && !stepData.userPrompt.trim().isEmpty()) {
                userPromptArea.setText(stepData.userPrompt);
                userPromptArea.setFont(defaultFont);
            } else {
                userPromptArea.setText(USER_PROMPT_PLACEHOLDER);
                userPromptArea.setFont(defaultFont.deriveFont(Font.ITALIC));
            }
        } else if ("Text Replacement".equals(stepData.type)) {
            textToReplaceField.setText(stepData.textToReplace);
            replacementTextField.setText(stepData.replacementText);
        } else if ("Synthesizer".equals(stepData.type)) {
            storedSynthesizerVoice = stepData.voiceId;
            storedSynthesizerProvider = (stepData.ttsProvider != null) ? stepData.ttsProvider : "ElevenLabs";
            originalSynthProvider = storedSynthesizerProvider;
            synthesizerProviderCombo.setSelectedItem(storedSynthesizerProvider);
            updateSynthesizerVoiceCombo();
            if (storedSynthesizerVoice != null) {
                synthesizerVoiceCombo.setSelectedItem(storedSynthesizerVoice);
            }
        }
        updateFieldsVisibility();
    }

    /**
     * Validates input according to processing type.
     *
     * @return true if the input is valid; otherwise, false.
     */
    public boolean isValidInput() {
        String type = (String) typeCombo.getSelectedItem();
        if ("Text Replacement".equals(type)) {
            if (textToReplaceField.getText().trim().isEmpty()) {
                textToReplaceField.setBorder(BorderFactory.createLineBorder(Color.RED));
                textToReplaceField.requestFocusInWindow();
                owner.scrollToComponent(textToReplaceField);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "For 'Text Replacement', the 'Text to Replace' field must be filled.");
                return false;
            } else {
                textToReplaceField.setBorder(defaultReplacementFieldBorder);
            }
        } else if ("Prompt".equals(type)) {
            String systemText = systemPromptArea.getText();
            String userText = userPromptArea.getText();
            if (systemText.equals(SYSTEM_PROMPT_PLACEHOLDER)) systemText = "";
            if (userText.equals(USER_PROMPT_PLACEHOLDER)) userText = "";
            boolean systemEmpty = systemText.trim().isEmpty();
            boolean userEmpty = userText.trim().isEmpty();
            if (systemEmpty && userEmpty) {
                userPromptArea.setBorder(BorderFactory.createLineBorder(Color.RED));
                userPromptArea.requestFocusInWindow();
                owner.scrollToComponent(systemPromptArea);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "For 'Prompt', at least 'System Prompt' or 'User Prompt' must be filled.");
                return false;
            } else {
                systemPromptArea.setBorder(defaultTextAreaBorder);
            }
            if (!userEmpty && !userText.contains("{{input}}")) {
                userPromptArea.setBorder(BorderFactory.createLineBorder(Color.ORANGE));
                userPromptArea.requestFocusInWindow();
                owner.scrollToComponent(userPromptArea);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                        "The User Prompt should include the placeholder '{{input}}' at least once.");
            }
        } else if ("Synthesizer".equals(type)) {
            if (synthesizerProviderCombo.getSelectedItem() == null ||
                    ((String) synthesizerProviderCombo.getSelectedItem()).trim().isEmpty()) {
                synthesizerProviderCombo.setBorder(BorderFactory.createLineBorder(Color.RED));
                synthesizerProviderCombo.requestFocusInWindow();
                owner.scrollToComponent(synthesizerProviderCombo);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "For 'Synthesizer', a valid provider must be selected.");
                return false;
            }
            if (synthesizerVoiceCombo.getItemCount() == 0 ||
                    synthesizerVoiceCombo.getSelectedItem() == null ||
                    ((String) synthesizerVoiceCombo.getSelectedItem()).contains("No voices loaded")) {
                synthesizerVoiceCombo.setBorder(BorderFactory.createLineBorder(Color.RED));
                synthesizerVoiceCombo.requestFocusInWindow();
                owner.scrollToComponent(synthesizerVoiceCombo);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "For 'Synthesizer', a valid voice must be selected.");
                return false;
            } else {
                synthesizerVoiceCombo.setBorder(UIManager.getBorder("ComboBox.border"));
            }
        }
        return true;
    }

    /**
     * Handles the Play/Stop button click for the Synthesizer step.
     * For OpenAI provider this button is hidden.
     */
    private void onPlayButtonClicked() {
        if (!"ElevenLabs".equals(synthesizerProviderCombo.getSelectedItem())) {
            return;
        }
        if (playButton.getText().equals("Play")) {
            playButton.setText("Stop");
            String selected = (String) synthesizerVoiceCombo.getSelectedItem();
            if (selected == null) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "No voice selected.");
                playButton.setText("Play");
                return;
            }
            org.whispercat.postprocessing.clients.ElevenLabsVoiceClient.VoiceData selectedVoiceData = null;
            for (org.whispercat.postprocessing.clients.ElevenLabsVoiceClient.VoiceData voice : owner.getElevenLabsVoices()) {
                if (voice.toString().equals(selected)) {
                    selectedVoiceData = voice;
                    break;
                }
            }
            if (selectedVoiceData == null) {
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Selected voice not found in loaded voices.");
                playButton.setText("Play");
                return;
            }
            String previewUrl = selectedVoiceData.getPreviewUrl();
            playbackWorker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    try {
                        byte[] audioBytes = owner.getElevenLabsClient().fetchPreviewURL(previewUrl);
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes)) {
                            audioPlayer = new Player(bais);
                            audioPlayer.play();
                        }
                    } catch (JavaLayerException ex) {
                        Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                                "Error playing audio sample: " + ex.getMessage());
                    }
                    return null;
                }

                @Override
                protected void done() {
                    playButton.setText("Play");
                }
            };
            playbackWorker.execute();
        } else {
            if (audioPlayer != null) {
                audioPlayer.close();
            }
            if (playbackWorker != null) {
                playbackWorker.cancel(true);
            }
            playButton.setText("Play");
        }
    }

    /**
     * Extracts data from this processing step.
     *
     * @return a ProcessingStepData object representing the step.
     */
    public ProcessingStepData getProcessingStepData() {
        ProcessingStepData stepData = new ProcessingStepData();
        stepData.type = (String) typeCombo.getSelectedItem();
        if ("Prompt".equals(stepData.type)) {
            stepData.provider = (String) providerCombo.getSelectedItem();
            stepData.model = (String) modelCombo.getSelectedItem();
            String sysText = systemPromptArea.getText();
            if (sysText.equals(SYSTEM_PROMPT_PLACEHOLDER)) sysText = "";
            stepData.systemPrompt = sysText;
            String userText = userPromptArea.getText();
            if (userText.equals(USER_PROMPT_PLACEHOLDER)) userText = "";
            stepData.userPrompt = userText;
        } else if ("Text Replacement".equals(stepData.type)) {
            stepData.textToReplace = textToReplaceField.getText();
            stepData.replacementText = replacementTextField.getText();
        } else if ("Synthesizer".equals(stepData.type)) {
            stepData.ttsProvider = (String) synthesizerProviderCombo.getSelectedItem();
            stepData.voiceId = (String) synthesizerVoiceCombo.getSelectedItem();
            if ("OpenAI".equals(stepData.ttsProvider)) {
                stepData.ttsModel = "tts-1";
            } else {
                stepData.ttsModel = "eleven_multilingual_v2";
            }
        }
        return stepData;
    }

    /**
     * Helper method to set a placeholder into a JTextArea.
     * When unfocused and empty, the placeholder is displayed in italic; when focused and matching the placeholder, it is cleared.
     *
     * @param textArea    the JTextArea
     * @param placeholder the placeholder text
     * @param defaultFont the default font
     */
    private void setPlaceholder(JTextArea textArea, String placeholder, Font defaultFont) {
        if (textArea.getText().trim().isEmpty()) {
            textArea.setFont(defaultFont.deriveFont(Font.ITALIC));
            textArea.setText(placeholder);
        } else {
            textArea.setFont(defaultFont);
        }
        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (textArea.getText().equals(placeholder)) {
                    textArea.setText("");
                    textArea.setFont(defaultFont);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (textArea.getText().trim().isEmpty()) {
                    textArea.setFont(defaultFont.deriveFont(Font.ITALIC));
                    textArea.setText(placeholder);
                } else {
                    textArea.setFont(defaultFont);
                }
            }
        });
    }
}