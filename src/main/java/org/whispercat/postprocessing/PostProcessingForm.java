package org.whispercat.postprocessing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.whispercat.*;
import org.whispercat.postprocessing.clients.OpenWebUIModelsResponse;
import org.whispercat.postprocessing.clients.OpenWebUIProcessClient;
import org.whispercat.postprocessing.clients.ElevenLabsVoiceClient;
import org.whispercat.recording.RecorderForm;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

public class PostProcessingForm extends JPanel {
    private final JTextField titleField;
    private final JTextField descriptionArea; // New Description Field
    private final ConfigManager configManager;
    private JButton addStepButton;
    private final JPanel stepsContainer;
    private JButton saveButton;
    // Declare scrollPane as a class member to allow automatic scrolling within this scroll pane.
    private final JScrollPane scrollPane;
    // A variable to store the default border for later resetting.
    private final Border defaultTextFieldBorder;
    private String currentUUID;
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(RecorderForm.class);

    private List<ElevenLabsVoiceClient.VoiceData> elevenLabsVoices = new ArrayList<>();


    // New fields for OpenWebUI-Provider:
    private List<String> openWebUIModelNames = new ArrayList<>();

    // New fields for ElevenLabs voices for Synthesizer steps.
    private List<String> elevenLabsVoiceNames = new ArrayList<>();
    private ElevenLabsVoiceClient elevenLabsVoiceClient;

    private OpenWebUIProcessClient openWebUIProcessClient;

    public PostProcessingForm(ConfigManager configManager, PostProcessingData existingJson) {
        this.configManager = configManager;
        // Set an empty border for spacing.
        setBorder(BorderFactory.createEmptyBorder(60, 20, 10, 10));
        setLayout(new BorderLayout());
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setAlignmentX(LEFT_ALIGNMENT);
        // Overall header: wrap in a FlowLayout.LEFT panel with no hgap.
        JLabel overallHeaderLabel = new JLabel("Post Processing Editor");
        overallHeaderLabel.setFont(overallHeaderLabel.getFont().deriveFont(Font.PLAIN, 18f));
        JPanel overallHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        overallHeaderPanel.add(overallHeaderLabel);
        overallHeaderPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        overallHeaderPanel.setAlignmentX(LEFT_ALIGNMENT);
        // topPanel.add(overallHeaderPanel);
        // Header panel for title and description.
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);
        // Create the title panel (using FlowLayout with no gap)
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel titleLabel = new JLabel("Post-Processing Title:");
        // Set the label to plain font.
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN));
        // Create the description panel (using FlowLayout with no gap)
        JPanel descriptionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        descriptionPanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel descriptionLabel = new JLabel("Description:");
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(Font.PLAIN));
        // Force both labels to have the same preferred width
        Dimension commonLabelSize = new Dimension(150, titleLabel.getPreferredSize().height);
        titleLabel.setPreferredSize(commonLabelSize);
        descriptionLabel.setPreferredSize(commonLabelSize);
        titlePanel.add(titleLabel);
        titleField = new JTextField(20);
        defaultTextFieldBorder = titleField.getBorder();
        // Make the text field expand horizontally.
        titleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleField.getPreferredSize().height));
        titlePanel.add(titleField);
        headerPanel.add(titlePanel);
        headerPanel.add(Box.createVerticalStrut(10));
        descriptionPanel.add(descriptionLabel);
        descriptionArea = new JTextField(40);
        descriptionArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, descriptionArea.getPreferredSize().height));
        descriptionPanel.add(descriptionArea);
        headerPanel.add(descriptionPanel);
        topPanel.add(headerPanel);
        add(topPanel, BorderLayout.NORTH);

        // Container for the Processing Steps.
        stepsContainer = new JPanel();

        stepsContainer.setTransferHandler(new PanelReorderTransferHandler());
        stepsContainer.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Component comp = stepsContainer.getComponentAt(e.getPoint());
                if (comp instanceof ProcessingStepPanel) {
                    stepsContainer.getTransferHandler().exportAsDrag(stepsContainer, e, TransferHandler.MOVE);
                }
            }
        });
        stepsContainer.setBorder(BorderFactory.createEmptyBorder());
        stepsContainer.setLayout(new BoxLayout(stepsContainer, BoxLayout.Y_AXIS));
        scrollPane = new JScrollPane(stepsContainer);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        add(scrollPane, BorderLayout.WEST);

        // Bottom Panel: Both buttons in one row.
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        saveButton = new JButton("Save");
        bottomPanel.add(saveButton);
        addStepButton = new JButton("Add Processing Step");
        bottomPanel.add(addStepButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Listener for adding a new Processing Step.
        addStepButton.addActionListener(e -> {
            ProcessingStepPanel stepPanel = new ProcessingStepPanel();
            stepsContainer.add(stepPanel);
            stepsContainer.revalidate();
            stepsContainer.repaint();
            SwingUtilities.invokeLater(() -> {
                JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                verticalBar.setValue(verticalBar.getMaximum());
            });
        });

        // Save button listener with validation.
        saveButton.addActionListener(e -> {
            if (!validateData()) {
                return;
            }
            PostProcessingData data = getPostProcessingData();
            configManager.savePostProcessingData(data);
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.SUCCESS, "Saved new processing.");
        });

        // Load existing data if provided.
        if (existingJson != null) {
            loadDataFromJson(existingJson);
        }
        // Initialize OpenWebUI client if settings are provided.
        if (!configManager.getOpenWebUIServerUrl().isEmpty() || !configManager.getOpenWebUIApiKey().isEmpty()) {
            openWebUIProcessClient = new OpenWebUIProcessClient(configManager);
            loadOpenWebUIModels();
        }
        // Initialize ElevenLabsVoiceClient if API key is set; otherwise show Notification.
        String elevenLabsApiKey = configManager.getProperty("elevenLabsApiKey");
        if (elevenLabsApiKey != null && !elevenLabsApiKey.trim().isEmpty()) {
            elevenLabsVoiceClient = new ElevenLabsVoiceClient(configManager);
            loadElevenLabsVoices();
        } else {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                    "ElevenLabs API key is missing. Synthesizer steps will not function properly.");
        }
    }

    /**
     * Loads all models from the Open WebUI server in the background and stores the names in openWebUIModelNames.
     * Afterwards, all ProcessingStepPanels that currently have "Open WebUI" as provider are updated.
     */
    private void loadOpenWebUIModels() {
        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                OpenWebUIModelsResponse modelsResponse = openWebUIProcessClient.fetchModels();
                return modelsResponse.getModelNames();
            }

            @Override
            protected void done() {
                try {
                    List<String> models = get();
                    openWebUIModelNames.clear();
                    openWebUIModelNames.addAll(models);
                    // Update all ProcessingStepPanels that use "Open WebUI" as provider.
                    for (Component comp : stepsContainer.getComponents()) {
                        if (comp instanceof ProcessingStepPanel) {
                            ProcessingStepPanel panel = (ProcessingStepPanel) comp;
                            if ("Open WebUI".equals(panel.getProvider())) {
                                panel.updateModelCombo();
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error loading Open WebUI models: ", ex);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Error loading Open WebUI models. See logs.");
                }
            }
        };
        worker.execute();
    }

    /**
     * Loads all voices from ElevenLabs in the background and stores the names in elevenLabsVoiceNames.
     * Then updates all ProcessingStepPanels of type "Synthesizer".
     */
    private void loadElevenLabsVoices() {
        SwingWorker<List<ElevenLabsVoiceClient.VoiceData>, Void> worker = new SwingWorker<List<ElevenLabsVoiceClient.VoiceData>, Void>() {
            @Override
            protected List<ElevenLabsVoiceClient.VoiceData> doInBackground() throws Exception {
                return elevenLabsVoiceClient.fetchVoices();
            }
            @Override
            protected void done() {
                try {
                    List<ElevenLabsVoiceClient.VoiceData> voices = get();
                    elevenLabsVoices.clear();
                    elevenLabsVoices.addAll(voices);
                    // Update all ProcessingStepPanels of type Synthesizer.
                    for (Component comp : stepsContainer.getComponents()) {
                        if (comp instanceof ProcessingStepPanel) {
                            ProcessingStepPanel panel = (ProcessingStepPanel) comp;
                            if ("Synthesizer".equals(panel.getType())) {
                                panel.updateSynthesizerVoiceCombo();
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error loading ElevenLabs voices: ", ex);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Error loading ElevenLabs voices. Check logs.");
                }
            }
        };
        worker.execute();
    }

    /**
     * Loads the JSON data into the panel.
     */
    public void loadDataFromJson(PostProcessingData data) {
        try {
            titleField.setText(data.title != null ? data.title : "");
            descriptionArea.setText(data.description != null ? data.description : "");
            // Save the loaded UUID (if available) into our currentUUID variable.
            currentUUID = data.uuid;
            stepsContainer.removeAll();
            if (data.steps != null) {
                for (ProcessingStepData stepData : data.steps) {
                    ProcessingStepPanel stepPanel = new ProcessingStepPanel();
                    stepPanel.loadStepData(stepData);
                    stepsContainer.add(stepPanel);
                }
            }
            stepsContainer.revalidate();
            stepsContainer.repaint();
        } catch (Exception ex) {
            logger.error(ex);
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                    "Error loading post processing data. See logs.");
        }
    }

    /**
     * Scrolls the scroll pane's viewport so that the specified component is visible.
     *
     * @param comp the component to scroll to.
     */
    private void scrollToComponent(Component comp) {
        if (scrollPane != null && scrollPane.getViewport() != null) {
            Rectangle rect = SwingUtilities.convertRectangle(comp.getParent(), comp.getBounds(), scrollPane.getViewport());
            scrollPane.getViewport().scrollRectToVisible(rect);
        }
    }

    /**
     * Validates that the title and processing steps have the required input.
     *
     * @return true if valid; otherwise, false.
     */
    private boolean validateData() {
        if (titleField.getText().trim().isEmpty()) {
            titleField.setBorder(BorderFactory.createLineBorder(Color.RED));
            titleField.requestFocusInWindow();
            scrollToComponent(titleField);
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                    "Title is mandatory.");
            return false;
        } else {
            titleField.setBorder(defaultTextFieldBorder);
        }
        for (Component comp : stepsContainer.getComponents()) {
            if (comp instanceof ProcessingStepPanel) {
                ProcessingStepPanel stepPanel = (ProcessingStepPanel) comp;
                if (!stepPanel.isValidInput()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Extracts the current settings and creates a PostProcessingData object.
     * Also ensures that a unique UUID is set if not already present.
     *
     * @return the PostProcessingData.
     */
    private PostProcessingData getPostProcessingData() {
        PostProcessingData data = new PostProcessingData();
        data.title = titleField.getText();
        data.description = descriptionArea.getText();
        data.steps = new ArrayList<>();
        for (Component comp : stepsContainer.getComponents()) {
            if (comp instanceof ProcessingStepPanel) {
                ProcessingStepPanel stepPanel = (ProcessingStepPanel) comp;
                data.steps.add(stepPanel.getProcessingStepData());
            }
        }
        // If there is no UUID (i.e. new post-processing), generate one.
        if (currentUUID == null || currentUUID.trim().isEmpty()) {
            currentUUID = UUID.randomUUID().toString();
        }
        data.uuid = currentUUID;
        return data;
    }

    private static class PanelTransferable implements Transferable {
        public static final DataFlavor PANEL_FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=javax.swing.JPanel", "JPanel");
        private final JPanel panel;
        public PanelTransferable(JPanel panel) {
            this.panel = panel;
        }
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { PANEL_FLAVOR };
        }
        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(PANEL_FLAVOR);
        }
        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (flavor.equals(PANEL_FLAVOR)) {
                return panel;
            }
            return null;
        }
    }

    private class PanelReorderTransferHandler extends TransferHandler {
        private JPanel draggedPanel;
        @Override
        protected Transferable createTransferable(JComponent c) {
            for (Component comp : stepsContainer.getComponents()) {
                if (comp.getBounds().contains(stepsContainer.getMousePosition())) {
                    draggedPanel = (JPanel) comp;
                    break;
                }
            }
            return new PanelTransferable(draggedPanel);
        }
        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }
        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            return support.isDataFlavorSupported(PanelTransferable.PANEL_FLAVOR);
        }
        @Override
        public boolean importData(TransferHandler.TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                JPanel droppedPanel = (JPanel) support.getTransferable().getTransferData(PanelTransferable.PANEL_FLAVOR);
                Point dropPoint = support.getDropLocation().getDropPoint();
                int index = -1;
                for (int i = 0; i < stepsContainer.getComponentCount(); i++) {
                    Component comp = stepsContainer.getComponent(i);
                    if (dropPoint.getY() < comp.getBounds().getCenterY()) {
                        index = i;
                        break;
                    }
                }
                if (index == -1) {
                    index = stepsContainer.getComponentCount();
                }
                stepsContainer.remove(droppedPanel);
                stepsContainer.add(droppedPanel, index);
                stepsContainer.revalidate();
                stepsContainer.repaint();
                return true;
            } catch (Exception e) {
                logger.error("Error during panel reorder: ", e);
            }
            return false;
        }
    }

    /**
     * Inner class describing a single Processing Step.
     */
    class ProcessingStepPanel extends JPanel {
        private String storedModel = "";
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

        private JComboBox<String> synthesizerProviderCombo; // New combo for the synthesizer provider
        private JComboBox<String> synthesizerVoiceCombo;
        private String storedSynthesizerVoice = null;
        private String storedSynthesizerProvider = "ElevenLabs"; // Default provider for synthesizer steps



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
        // Default sample ID to be used in API call.

        public ProcessingStepPanel() {
            setBorder(BorderFactory.createTitledBorder("Processing Step"));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
            add(Box.createVerticalStrut(10));
            // Top Panel with Processing Type and Remove Button remains unchanged…
            JPanel topPanel = new JPanel(new BorderLayout());
            JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            typePanel.add(new JLabel("Processing Type:"));
            // Added new type "Synthesizer" along with existing types.
            typeCombo = new JComboBox<>(new String[]{"Prompt", "Text Replacement", "Synthesizer"});
            typePanel.add(typeCombo);
            topPanel.add(Box.createVerticalStrut(10));
            topPanel.add(typePanel, BorderLayout.WEST);
            JButton removeButton = new JButton();
            Icon trashIcon = new FlatSVGIcon("icon/svg/trash.svg", 16, 16);
            removeButton.setIcon(trashIcon);
            removeButton.setToolTipText("Remove this Processing Step");
            removeButton.addActionListener((ActionEvent e) -> {
                stepsContainer.remove(ProcessingStepPanel.this);
                stepsContainer.revalidate();
                stepsContainer.repaint();
            });
            JPanel removePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            removePanel.add(removeButton);
            topPanel.add(removePanel, BorderLayout.EAST);
            add(topPanel);
            // Definition of Provider and Model Panel (for Prompt type) remains unchanged...
            JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            JLabel providerLabel = new JLabel("Provider:");
            providerPanel.add(providerLabel);
            providerPanel.add(Box.createHorizontalStrut(5));
            providerCombo = new JComboBox<>(new String[]{"OpenAI", "Open WebUI"});
            providerPanel.add(providerCombo);
            providerPanel.add(Box.createHorizontalStrut(15));
            JLabel modelLabel = new JLabel("Model:");
            providerPanel.add(modelLabel);
            providerPanel.add(Box.createHorizontalStrut(5));
            modelCombo = new JComboBox<>(new String[]{"gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"});
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

            synthesizerPanel = new JPanel();
            synthesizerPanel.setLayout(new BoxLayout(synthesizerPanel, BoxLayout.Y_AXIS));
            synthesizerPanel.add(Box.createVerticalStrut(20));

            JPanel synthSubPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            JLabel synthProviderLabel = new JLabel("Provider:");
            synthSubPanel.add(synthProviderLabel);
            synthSubPanel.add(Box.createHorizontalStrut(5));
            synthesizerProviderCombo = new JComboBox<>(new String[]{"ElevenLabs"});
            synthesizerProviderCombo.setSelectedItem("11 Labs");
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
            synthSubPanel.add(playButton);
            playButton.addActionListener(e -> onPlayButtonClicked());

            synthesizerPanel.add(synthSubPanel);
            add(synthesizerPanel);

            // Update field visibility based on selected type.
            updateFieldsVisibility();
            typeCombo.addActionListener(e -> updateFieldsVisibility());
            attachTextAreaForwarder(systemPromptArea, scrollPane);
            attachTextAreaForwarder(userPromptArea, scrollPane);
            setPlaceholder(systemPromptArea, SYSTEM_PROMPT_PLACEHOLDER, defaultFont);
            setPlaceholder(userPromptArea, USER_PROMPT_PLACEHOLDER, defaultFont);
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
                // Notify if ElevenLabs API key is missing
                if (configManager.getProperty("elevenLabsApiKey") == null || configManager.getProperty("elevenLabsApiKey").trim().isEmpty()) {
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                            "ElevenLabs API key is missing. Please set it in the settings.");
                }
            }
            revalidate();
            repaint();
        }

        /**
         * Updates the model combo for "Prompt" type based on the selected provider.
         */
        public void updateModelCombo() {
            String provider = (String) providerCombo.getSelectedItem();
            String previousSelection = (storedModel != null) ? storedModel : "";
            modelCombo.removeAllItems();
            if ("Open WebUI".equals(provider)) {
                if (openWebUIModelNames == null || openWebUIModelNames.isEmpty()) {
                    if (!previousSelection.isEmpty()) {
                        modelCombo.addItem(previousSelection);
                        modelCombo.setSelectedItem(previousSelection);
                    } else {
                        modelCombo.addItem("No models loaded");
                    }
                    return;
                }
                boolean loadedContainsPrevious = false;
                for (String name : openWebUIModelNames) {
                    modelCombo.addItem(name);
                    if (name.equals(previousSelection)) {
                        loadedContainsPrevious = true;
                    }
                }
                if (!previousSelection.isEmpty() && !loadedContainsPrevious) {
                    modelCombo.insertItemAt(previousSelection, 0);
                    modelCombo.setSelectedItem(previousSelection);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                            "The previously selected Open WebUI model \"" + previousSelection + "\" is not available anymore.");
                } else if (loadedContainsPrevious) {
                    modelCombo.setSelectedItem(previousSelection);
                }
            } else { // OpenAI
                String previous = previousSelection;
                String[] openaiModels = {"gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"};
                for (String m : openaiModels) {
                    modelCombo.addItem(m);
                }
                for (int i = 0; i < modelCombo.getItemCount(); i++) {
                    if (modelCombo.getItemAt(i).equals(previous)) {
                        modelCombo.setSelectedItem(previous);
                        break;
                    }
                }
            }
        }

        /**
         * Updates the synthesizer voice combo using the ElevenLabs voice data loaded in the outer class.
         * If no voices are loaded yet, it still inserts the saved voice (if any) so that it remains visible.
         */
        public void updateSynthesizerVoiceCombo() {
            synthesizerVoiceCombo.removeAllItems();
            if (elevenLabsVoices == null || elevenLabsVoices.isEmpty()) {
                if (storedSynthesizerVoice != null && !storedSynthesizerVoice.trim().isEmpty()) {
                    synthesizerVoiceCombo.addItem(storedSynthesizerVoice);
                } else {
                    synthesizerVoiceCombo.addItem("Loading voices...");
                }
            } else {
                for (ElevenLabsVoiceClient.VoiceData voice : elevenLabsVoices) {
                    synthesizerVoiceCombo.addItem(voice.toString());
                }
                if (storedSynthesizerVoice != null) {
                    synthesizerVoiceCombo.setSelectedItem(storedSynthesizerVoice);
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
         *
         * @return the provider as a String.
         */
        public String getProvider() {
            // For Synthesizer steps, the provider is implicitly "ElevenLabs".
            if ("Synthesizer".equals(getType())) {
                return "ElevenLabs";
            }
            return (String) providerCombo.getSelectedItem();
        }

        /**
         * Loads data from the provided ProcessingStepData and updates the fields.
         *
         * @param stepData the data for this processing step.
         */
        public void loadStepData(ProcessingStepData stepData) {
            typeCombo.setSelectedItem(stepData.type);
            if ("Prompt".equals(stepData.type)) {
                storedModel = stepData.model;
                providerCombo.setSelectedItem(stepData.provider);
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
                // Store the saved values for later re-selection.
                storedSynthesizerVoice = stepData.ttsModel;
                storedSynthesizerProvider = stepData.ttsProvider != null ? stepData.ttsProvider : "ElevenLabs";
                // Set the provider combo and then update the voice combo.
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
         */
        public boolean isValidInput() {
            String type = (String) typeCombo.getSelectedItem();
            if ("Text Replacement".equals(type)) {
                if (textToReplaceField.getText().trim().isEmpty()) {
                    textToReplaceField.setBorder(BorderFactory.createLineBorder(Color.RED));
                    textToReplaceField.requestFocusInWindow();
                    PostProcessingForm.this.scrollToComponent(textToReplaceField);
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
                    PostProcessingForm.this.scrollToComponent(systemPromptArea);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "For 'Prompt', at least 'System Prompt' or 'User Prompt' must be filled.");
                    return false;
                } else {
                    systemPromptArea.setBorder(defaultTextAreaBorder);
                }
                if (!userEmpty && !userText.contains("{{input}}")) {
                    userPromptArea.setBorder(BorderFactory.createLineBorder(Color.ORANGE));
                    userPromptArea.requestFocusInWindow();
                    PostProcessingForm.this.scrollToComponent(userPromptArea);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                            "The User Prompt should include the placeholder '{{input}}' at least once.");
                }
            } else if ("Synthesizer".equals(type)) {
                if (synthesizerProviderCombo.getSelectedItem() == null ||
                        ((String)synthesizerProviderCombo.getSelectedItem()).trim().isEmpty()) {
                    synthesizerProviderCombo.setBorder(BorderFactory.createLineBorder(Color.RED));
                    synthesizerProviderCombo.requestFocusInWindow();
                    PostProcessingForm.this.scrollToComponent(synthesizerProviderCombo);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "For 'Synthesizer', a valid provider must be selected.");
                    return false;
                }
                if (synthesizerVoiceCombo.getItemCount() == 0 ||
                        synthesizerVoiceCombo.getSelectedItem() == null ||
                        ((String)synthesizerVoiceCombo.getSelectedItem()).contains("No voices loaded")) {
                    synthesizerVoiceCombo.setBorder(BorderFactory.createLineBorder(Color.RED));
                    synthesizerVoiceCombo.requestFocusInWindow();
                    PostProcessingForm.this.scrollToComponent(synthesizerVoiceCombo);
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
         */
        private void onPlayButtonClicked() {
            // If not in playback mode, then try to play.
            if (playButton.getText().equals("Play")) {
                playButton.setText("Stop");
                // Get the selected string from the synthesizerVoiceCombo
                String selected = (String) synthesizerVoiceCombo.getSelectedItem();
                if (selected == null) {
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "No voice selected.");
                    playButton.setText("Play");
                    return;
                }
                // Search for the VoiceData that matches the selected string.
                ElevenLabsVoiceClient.VoiceData selectedVoiceData = null;
                for (ElevenLabsVoiceClient.VoiceData voice : elevenLabsVoices) {
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
                String voiceId = selectedVoiceData.getVoiceId();
                String previewUrl = selectedVoiceData.getPreviewUrl(); // This sample id is taken from the API response or default.
                // Start playback in a background SwingWorker.
                playbackWorker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        try {
                            byte[] audioBytes = elevenLabsVoiceClient.fetchPreviewURL(previewUrl);
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
            } else { // Stop button clicked.
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
         * Returns the data extracted from this processing step.
         *
         * @return a ProcessingStepData object representing the step.
         */
        /**
         * Extracts data from this processing step.
         */
        public ProcessingStepData getProcessingStepData() {
            ProcessingStepData stepData = new ProcessingStepData();
            stepData.type = (String) typeCombo.getSelectedItem();
            if ("Prompt".equals(stepData.type)) {
                stepData.provider = (String) providerCombo.getSelectedItem();
                stepData.model = (String) modelCombo.getSelectedItem();
                String sysText = systemPromptArea.getText();
                if (sysText.equals(SYSTEM_PROMPT_PLACEHOLDER)) sysText="";
                stepData.systemPrompt = sysText;
                String userText = userPromptArea.getText();
                if (userText.equals(USER_PROMPT_PLACEHOLDER)) userText="";
                stepData.userPrompt = userText;
            } else if ("Text Replacement".equals(stepData.type)) {
                stepData.textToReplace = textToReplaceField.getText();
                stepData.replacementText = replacementTextField.getText();
            } else if ("Synthesizer".equals(stepData.type)) {
                stepData.ttsProvider = (String) synthesizerProviderCombo.getSelectedItem();
                stepData.ttsModel = (String) synthesizerVoiceCombo.getSelectedItem();
            }
            return stepData;
        }
    }

    /**
     * A helper method to set a placeholder into a JTextArea.
     * When the text area is unfocused and empty, the placeholder is displayed in italic font.
     * When the user focuses the field and the text equals the placeholder, it is cleared.
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

    /**
     * MouseWheelListener that scrolls the given target JScrollPane when the JTextArea is not focused.
     * If the JTextArea is focused, the standard scrolling behavior of the text area occurs.
     */
    private static class TextAreaScrollForwarder implements MouseWheelListener {
        private final JScrollPane targetScrollPane;
        public TextAreaScrollForwarder(JScrollPane targetScrollPane) {
            this.targetScrollPane = targetScrollPane;
        }
        public JScrollPane getTargetScrollPane() {
            return targetScrollPane;
        }
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            Component source = e.getComponent();
            if (source instanceof JTextArea && source.isFocusOwner()) {
                JScrollPane localScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, source);
                if (localScrollPane != null) {
                    JScrollBar verticalBar = localScrollPane.getVerticalScrollBar();
                    int scrollAmount = e.getWheelRotation() * verticalBar.getUnitIncrement();
                    verticalBar.setValue(verticalBar.getValue() + scrollAmount);
                    e.consume();
                    return;
                }
            }
            JScrollBar verticalBar = targetScrollPane.getVerticalScrollBar();
            int scrollAmount = e.getWheelRotation() * verticalBar.getUnitIncrement();
            verticalBar.setValue(verticalBar.getValue() + scrollAmount);
            e.consume();
        }
    }

    /**
     * Recursively traverses from the given component and attaches a TextAreaScrollForwarder
     * (constructed with the provided target scroll pane) to every JTextArea found.
     *
     * @param comp             The root component to search.
     * @param targetScrollPane The JScrollPane whose scrolling is to be controlled.
     */
    private static void attachTextAreaForwarder(Component comp, JScrollPane targetScrollPane) {
        if (comp instanceof JTextArea) {
            JTextArea textArea = (JTextArea) comp;
            boolean exists = false;
            for (MouseWheelListener listener : textArea.getMouseWheelListeners()) {
                if (listener instanceof TextAreaScrollForwarder) {
                    TextAreaScrollForwarder forwarder = (TextAreaScrollForwarder) listener;
                    if (forwarder.getTargetScrollPane() == targetScrollPane) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                textArea.addMouseWheelListener(new TextAreaScrollForwarder(targetScrollPane));
            }
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Post Processing");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(new PostProcessingForm(null, null));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}