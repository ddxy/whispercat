package org.whispercat.postprocessing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.whispercat.*;
import org.whispercat.postprocessing.clients.OpenWebUIModelsResponse;
import org.whispercat.postprocessing.clients.OpenWebUIProcessClient;
import org.whispercat.recording.RecorderForm;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    // Neue Felder für Open WebUI-Provider:
    private List<String> openWebUIModelNames = new ArrayList<>();
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

        if ((!configManager.getOpenWebUIServerUrl().isEmpty() || !configManager.getOpenWebUIApiKey().isEmpty()) && existingJson != null) {
            openWebUIProcessClient = new OpenWebUIProcessClient(configManager);
            loadOpenWebUIModels();
        }


    }

    /**
     * Lädt alle Modelle vom Open WebUI-Server im Hintergrund und speichert die Namen in openWebUIModelNames.
     * Anschließend werden alle ProcessingStepPanels, die gerade "Open WebUI" als Provider haben, aktualisiert.
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
                    // Aktualisiere alle ProcessingStepPanels, die "Open WebUI" als Provider nutzen.
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

    /**
     * Inner class describing a single Processing Step.
     */
    class ProcessingStepPanel extends JPanel {
        private String storedModel = "";
        private JComboBox<String> typeCombo;
        private JPanel promptPanel;
        private JPanel replacementPanel;
        private JTextArea systemPromptArea;
        private JTextArea userPromptArea;
        private Font defaultFont = new JTextArea().getFont();
        // Provider- und Model-Combo; nun mit Open WebUI als Auswahl.
        private JComboBox<String> providerCombo;
        private JComboBox<String> modelCombo;
        private JTextField textToReplaceField;
        private JTextField replacementTextField;
        private Border defaultTextAreaBorder;
        private Border defaultReplacementFieldBorder;
        private final String SYSTEM_PROMPT_PLACEHOLDER = "Enter system instructions, e.g., 'You are a helpful assistant.'";
        private final String USER_PROMPT_PLACEHOLDER = "Enter a user message template. For example: 'Greetings, {{input}}! Welcome to our service.' You can include the placeholder {{input}} to insert user input (this may be repeated several times).";

        public ProcessingStepPanel() {
            setBorder(BorderFactory.createTitledBorder("Processing Step"));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
            add(Box.createVerticalStrut(10));
            JPanel topPanel = new JPanel(new BorderLayout());
            JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            typePanel.add(new JLabel("Processing Type:"));
            typeCombo = new JComboBox<>(new String[]{"Prompt", "Text Replacement"});
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

            // Definition des Provider- und Model- Bereichs:
            JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            JLabel providerLabel = new JLabel("Provider:");
            providerPanel.add(providerLabel);
            providerPanel.add(Box.createHorizontalStrut(5));
            // Erweitert um "Open WebUI"
            providerCombo = new JComboBox<>(new String[]{"OpenAI", "Open WebUI"});
            providerPanel.add(providerCombo);
            providerPanel.add(Box.createHorizontalStrut(15));
            JLabel modelLabel = new JLabel("Model:");
            providerPanel.add(modelLabel);
            providerPanel.add(Box.createHorizontalStrut(5));
            modelCombo = new JComboBox<>(new String[]{"gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"});
            providerPanel.add(modelCombo);
            // Bei Änderung des Providers wird die Modell-Combo aktualisiert.
            providerCombo.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    updateModelCombo();
                }
            });

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
            add(Box.createVerticalStrut(10));
            replacementPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            replacementPanel.add(new JLabel("Text to Replace:"));
            textToReplaceField = new JTextField(10);
            defaultReplacementFieldBorder = textToReplaceField.getBorder();
            replacementPanel.add(textToReplaceField);
            replacementPanel.add(new JLabel("Replacement Text:"));
            replacementTextField = new JTextField(10);
            replacementPanel.add(replacementTextField);
            add(replacementPanel);
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

        private void updateFieldsVisibility() {
            String selection = (String) typeCombo.getSelectedItem();
            if ("Prompt".equals(selection)) {
                promptPanel.setVisible(true);
                replacementPanel.setVisible(false);
            } else if ("Text Replacement".equals(selection)) {
                promptPanel.setVisible(false);
                replacementPanel.setVisible(true);
            }
            // Aktualisiere ggf. die Modell-Auswahl, falls Provider "Open WebUI" gewählt ist.
            //updateModelCombo();
            revalidate();
            repaint();
        }
        /**
         * Aktualisiert die Modell-Combo basierend auf dem ausgewählten Provider.
         * Verwendet den in storedModel gespeicherten Wert zur Wiederherstellung der Auswahl.
         */
        public void updateModelCombo() {
            String provider = (String) providerCombo.getSelectedItem();
            // Statt modelCombo.getSelectedItem() verwenden wir storedModel
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
         * Liefert den aktuell gewählten Provider.
         *
         * @return den Provider als String.
         */
        public String getProvider() {
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
                storedModel = stepData.model; // Hier den gespeicherten Wert übernehmen.
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
            }
            updateFieldsVisibility();
        }
        /**
         * Validates input according to processing type, taking into account placeholder text.
         *
         * @return true if valid; otherwise, false.
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
                if (systemText.equals(SYSTEM_PROMPT_PLACEHOLDER)) {
                    systemText = "";
                }
                if (userText.equals(USER_PROMPT_PLACEHOLDER)) {
                    userText = "";
                }
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
            }
            return true;
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
                if (sysText.equals(SYSTEM_PROMPT_PLACEHOLDER)) {
                    sysText = "";
                }
                stepData.systemPrompt = sysText;
                String userText = userPromptArea.getText();
                if (userText.equals(USER_PROMPT_PLACEHOLDER)) {
                    userText = "";
                }
                stepData.userPrompt = userText;
            } else if ("Text Replacement".equals(stepData.type)) {
                stepData.textToReplace = textToReplaceField.getText();
                stepData.replacementText = replacementTextField.getText();
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