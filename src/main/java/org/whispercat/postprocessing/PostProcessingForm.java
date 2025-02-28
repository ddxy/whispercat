package org.whispercat.postprocessing;

import org.whispercat.*;
import org.whispercat.postprocessing.clients.*;
import org.whispercat.recording.RecorderForm;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// PostProcessingForm now implements ProcessingStepPanelOwner.
// All previously existing functionality and UI elements remain intact.
public class PostProcessingForm extends JPanel implements ProcessingStepPanelOwner {
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

    private LiteLLMProcessClient liteLLMProcessClient;
    private List<String> liteLLMModelNames = new ArrayList<>();


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
        // Header panel for title and description.
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);
        // Create the title panel (using FlowLayout with no gap)
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel titleLabel = new JLabel("Post-Processing Title:");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN));
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
            ProcessingStepPanel stepPanel = new ProcessingStepPanel(this);
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

        // Initialize OpenWebUI client if settings are provided.
        if (!configManager.getLiteLLMServerUrl().isEmpty() || !configManager.getLiteLLMApiKey().isEmpty()) {
            liteLLMProcessClient = new LiteLLMProcessClient(configManager);
            loadLiteLLMModels();
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
     * Loads all models from the Open WebUI server in the background and stores the names in openWebUIModelNames.
     * Afterwards, all ProcessingStepPanels that currently have "Open WebUI" as provider are updated.
     */
    private void loadLiteLLMModels() {
        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                LiteLLMModelsResponse liteLLMModelsResponse = liteLLMProcessClient.fetchModels();
                return liteLLMModelsResponse.getModelIds();
            }
            @Override
            protected void done() {
                try {
                    List<String> models = get();
                    liteLLMModelNames.clear();
                    liteLLMModelNames.addAll(models);
                    // Update all ProcessingStepPanels that use "Open WebUI" as provider.
                    for (Component comp : stepsContainer.getComponents()) {
                        if (comp instanceof ProcessingStepPanel) {
                            ProcessingStepPanel panel = (ProcessingStepPanel) comp;
                            if ("LiteLLM".equals(panel.getProvider())) {
                                panel.updateModelCombo();
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error loading Open WebUI models: ", ex);
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Error loading LiteLLM models. See logs.");
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
                    ProcessingStepPanel stepPanel = new ProcessingStepPanel(this);
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
    public void scrollToComponent(Component comp) {
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

    // Implementation of ProcessingStepPanelOwner interface methods.
    @Override
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public List<String> getOpenWebUIModelNames() {
        return openWebUIModelNames;
    }

    @Override
    public List<String> liteLLMModelNames() {
        return liteLLMModelNames;
    }

    @Override
    public List<ElevenLabsVoiceClient.VoiceData> getElevenLabsVoices() {
        return elevenLabsVoices;
    }

    @Override
    public ElevenLabsVoiceClient getElevenLabsClient() {
        return elevenLabsVoiceClient;
    }

    @Override
    public void removeProcessingStep(ProcessingStepPanel panel) {
        stepsContainer.remove(panel);
        stepsContainer.revalidate();
        stepsContainer.repaint();
    }

    /**
     * Custom TransferHandler for reordering processing panels.
     */
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
                if(index > stepsContainer.getComponentCount()) {
                    index = stepsContainer.getComponentCount();
                }
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
     * MouseWheelListener that scrolls the given target JScrollPane when the JTextArea is not focused.
     * (This method is kept for compatibility – you can further refactor it if necessary.)
     */
    public static class TextAreaScrollForwarder implements MouseWheelListener {
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
            if(targetScrollPane != null) {
                JScrollBar verticalBar = targetScrollPane.getVerticalScrollBar();
                int scrollAmount = e.getWheelRotation() * verticalBar.getUnitIncrement();
                verticalBar.setValue(verticalBar.getValue() + scrollAmount);
            }
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
    public static void attachTextAreaForwarder(Component comp, JScrollPane targetScrollPane) {
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
            if (!exists && targetScrollPane != null) {
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