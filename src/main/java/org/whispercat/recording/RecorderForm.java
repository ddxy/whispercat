package org.whispercat.recording;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import org.whispercat.*;
import org.whispercat.postprocessing.PostProcessingData;
import org.whispercat.postprocessing.PostProcessingService;
import org.whispercat.recording.clients.*;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class RecorderForm extends javax.swing.JPanel {
    private final JTextArea processedText = new JTextArea(3, 20);
    // Added missing declaration of enablePostProcessingCheckBox.
    private final JCheckBox enablePostProcessingCheckBox = new JCheckBox("<html>Enable Post Processing&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</html>");

    // Removed checkboxes recordAudioOutputCheckBox and doNotRecordMicrophoneCheckBox

    // Added radio buttons for recording mode selection:
    private JRadioButton recordMicOnlyRadioButton;
    private JRadioButton recordMicAndAudioRadioButton;
    private JRadioButton recordAudioOnlyRadioButton;

    private final JButton recordButton;
    private final int baseIconSize = 200;
    private final OpenAITranscribeClient openAITranscribeClient;
    private final ConfigManager configManager;
    private final FasterWhisperTranscribeClient fasterWhisperTranscribeClient;
    private final OpenWebUITranscribeClient openWebUITranscribeClient;
    private final LiteLLMRecordingClient liteLLMRecordingClient;
    private boolean isRecording = false;
    private AudioRecorder recorder;
    private final JTextArea transcriptionTextArea;
    private final JLabel recordingLabel;
    private JButton copyButton;
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(RecorderForm.class);
    private JComboBox<PostProcessingItem> postProcessingSelectComboBox;
    private List<PostProcessingData> postProcessingJSONList;
    private SwingWorker<File, Void> speakerWorker;
    // Instance variable for speaker recorder remains unchanged.
    private SpeakerRecorder speakerRecorder;
    // Instance variables for storing recorded files:
    private File lastRecordedMicFile;
    private File lastRecordedSpeakerFile;

    // Advanced settings output device components remain unchanged.
    private JComboBox<String> outputDeviceComboBox;
    private JButton testOutputButton;
    private JProgressBar outputProgressBar;

    public RecorderForm(ConfigManager configManager) {
        this.configManager = configManager;
        this.openAITranscribeClient = new OpenAITranscribeClient(configManager);
        this.fasterWhisperTranscribeClient = new FasterWhisperTranscribeClient(configManager);
        this.openWebUITranscribeClient = new OpenWebUITranscribeClient(configManager);
        this.liteLLMRecordingClient = new LiteLLMRecordingClient(configManager);
        // Instantiate the new SpeakerRecorder
        this.speakerRecorder = new SpeakerRecorder();

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(new EmptyBorder(50, 50, 10, 50));
        int iconSize = UIScale.scale(baseIconSize);
        FlatSVGIcon micIcon = new FlatSVGIcon("whispercat.svg", iconSize, iconSize);
        recordingLabel = new JLabel(micIcon);
        recordingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        recordingLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        recordingLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleRecording();
            }
        });
        JLabel recordingStatusLabel = new JLabel("Recording status:");
        recordingStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        recordButton = new JButton("Start Recording");
        recordButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        recordButton.addActionListener(e -> {
            toggleRecording();
        });
        JPanel transcriptionPanel = new JPanel();
        transcriptionPanel.setLayout(new BoxLayout(transcriptionPanel, BoxLayout.Y_AXIS));
        transcriptionTextArea = new JTextArea(3, 20);
        transcriptionTextArea.setLineWrap(true);
        transcriptionTextArea.setWrapStyleWord(true);
        transcriptionTextArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JScrollPane transcriptionTextScrollPane = new JScrollPane(transcriptionTextArea);
        transcriptionTextScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        transcriptionTextScrollPane.setMinimumSize(new Dimension(600, transcriptionTextArea.getPreferredSize().height + 10));
        transcriptionPanel.add(transcriptionTextScrollPane);
        copyButton = new JButton("Copy");
        copyButton.setToolTipText("Copy transcription to clipboard");
        copyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        copyButton.addActionListener(e -> copyTranscriptionToClipboard(transcriptionTextArea.getText()));
        centerPanel.add(recordingLabel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(recordButton);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(transcriptionPanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(copyButton);
        JLabel dragDropLabel = new JLabel("Drag & drop an audio file here.");
        dragDropLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        dragDropLabel.setForeground(Color.GRAY);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(dragDropLabel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(Box.createVerticalGlue());
        centerPanel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                // Allow file list import
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.List<File> fileList = (java.util.List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!fileList.isEmpty()) {
                        File droppedFile = fileList.get(0);
                        String lowerName = droppedFile.getName().toLowerCase();
                        if (!(lowerName.endsWith(".wav") || lowerName.endsWith(".mp3"))) {
                            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING, "Only .wav and .mp3 files allowed.");
                            return false;
                        }
                        // Call the unified stopRecording method with the dropped file.
                        customUpload(droppedFile);
                        return true;
                    }
                } catch (Exception ex) {
                    logger.error("Error importing dropped file", ex);
                }
                return false;
            }
        });
        // ===== Begin Advanced Settings Panel =====
        JPanel advancedSettingsContainerPanel = new JPanel();
        advancedSettingsContainerPanel.setLayout(new BoxLayout(advancedSettingsContainerPanel, BoxLayout.Y_AXIS));
        // CHANGED: Removed top inset (changed from 10 to 0) to adjust spacing between Post Processing and Advanced Settings panels.
        advancedSettingsContainerPanel.setBorder(new EmptyBorder(0, 50, 0, 50));
        advancedSettingsContainerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // New checkbox to enable advanced recording settings
        JCheckBox enableAdvancedSettingsCheckBox = new JCheckBox("Show Advanced Settings");
        enableAdvancedSettingsCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        enableAdvancedSettingsCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        advancedSettingsContainerPanel.add(enableAdvancedSettingsCheckBox);
        advancedSettingsContainerPanel.add(Box.createVerticalStrut(10));
        // Panel for additional advanced settings similar to postProcessingContainerPanel
        JPanel advancedSettingsPanel = new JPanel();
        advancedSettingsPanel.setLayout(new BoxLayout(advancedSettingsPanel, BoxLayout.Y_AXIS));
        advancedSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JCheckBox autoPasteCheckBox = new JCheckBox("Paste Transcription from clipboard (like Ctrl+V)");
        autoPasteCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        autoPasteCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoPasteCheckBox.setSelected(configManager.isAutoPasteEnabled());
        autoPasteCheckBox.addActionListener(e -> {
            configManager.setAutoPasteEnabled(autoPasteCheckBox.isSelected());
        });
        advancedSettingsPanel.add(autoPasteCheckBox);
        // ===== New Advanced-Settings for Recording Mode via Radio Buttons =====
        JPanel speakerRecordingPanel = new JPanel();
        speakerRecordingPanel.setLayout(new BoxLayout(speakerRecordingPanel, BoxLayout.Y_AXIS));
        speakerRecordingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // CHANGED: Remove old checkboxes and add radio buttons for selecting the recording mode.
        // The three options are: "Record only Microphone (Default)", "Record Microphone and Audio", and "Record only Audio"
        speakerRecordingPanel.setBorder(BorderFactory.createTitledBorder("Recording Mode"));
        recordMicOnlyRadioButton = new JRadioButton("Record only Microphone (Default)");
        recordMicAndAudioRadioButton = new JRadioButton("Record Microphone and Audio");
        JLabel infoLabel = new JLabel("Info: additional details here");

        recordAudioOnlyRadioButton = new JRadioButton("Record only Audio");

        // Set default selection
        recordMicOnlyRadioButton.setSelected(true);

        ButtonGroup recordingModeGroup = new ButtonGroup();
        recordingModeGroup.add(recordMicOnlyRadioButton);
        recordingModeGroup.add(recordMicAndAudioRadioButton);
        recordMicAndAudioRadioButton.setToolTipText("Currently recording both works only with OpenAI API directly and FasterWhisper API. OpenWebUI and LiteLLM do not support this word segmentation.");
        if (configManager.getWhisperServer().equals("Open WebUI") || configManager.getWhisperServer().equals("LiteLLM")) {
            recordMicAndAudioRadioButton.setEnabled(false);
        }

        recordingModeGroup.add(recordAudioOnlyRadioButton);

        speakerRecordingPanel.add(recordMicOnlyRadioButton);
        speakerRecordingPanel.add(recordMicAndAudioRadioButton);
        speakerRecordingPanel.add(recordAudioOnlyRadioButton);
        speakerRecordingPanel.add(Box.createVerticalStrut(5));

        // Panel for output device controls remains unchanged
        JPanel outputDevicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        outputDevicePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel outputDeviceLabel = new JLabel("Select Monitor Device:");
        outputDevicePanel.add(outputDeviceLabel);
        outputDeviceComboBox = new JComboBox<>();
        String[] monitorDevices = SpeakerRecorder.getMonitorDevices();
        if (monitorDevices.length == 0) {
            outputDeviceComboBox.addItem("No monitor devices found");
        } else {
            String lastUsedOutputDevice = configManager.getLastUsedOutputDevice();
            int selectedIndex = 0;
            for (int i = 0; i < monitorDevices.length; i++) {
                String device = monitorDevices[i];
                if (device.equals(lastUsedOutputDevice)) {
                    selectedIndex = i;
                }
                outputDeviceComboBox.addItem(device);
            }
            outputDeviceComboBox.setSelectedIndex(selectedIndex);
        }
        outputDevicePanel.add(outputDeviceComboBox);
        testOutputButton = new JButton("Test Audio");
        outputDevicePanel.add(testOutputButton);
        outputProgressBar = new JProgressBar();
        outputProgressBar.setPreferredSize(new Dimension(100, 20));
        outputProgressBar.setIndeterminate(false);
        outputProgressBar.setStringPainted(true);
        outputProgressBar.setString("Idle");
        outputDevicePanel.add(outputProgressBar);
        speakerRecordingPanel.add(outputDevicePanel);
        testOutputButton.addActionListener(e -> {
            String selectedDevice = (String) outputDeviceComboBox.getSelectedItem();
            if (selectedDevice == null || selectedDevice.equals("No monitor devices found")) {
                JOptionPane.showMessageDialog(this,
                        "No valid monitor device selected!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            speakerRecorder.testAudioOutput(selectedDevice, outputProgressBar);
        });
        advancedSettingsPanel.setVisible(false);
        enableAdvancedSettingsCheckBox.addActionListener(e -> {
            boolean selected = enableAdvancedSettingsCheckBox.isSelected();
            advancedSettingsPanel.setVisible(selected);
            speakerRecordingPanel.setVisible(selected);
            advancedSettingsContainerPanel.revalidate();
            advancedSettingsContainerPanel.repaint();
        });
        advancedSettingsPanel.add(Box.createVerticalStrut(10));
        advancedSettingsPanel.add(speakerRecordingPanel);
        advancedSettingsContainerPanel.add(advancedSettingsPanel);
        // ===== End Advanced Settings Panel =====
        JPanel postProcessingContainerPanel = new JPanel();
        postProcessingContainerPanel.setLayout(new BoxLayout(postProcessingContainerPanel, BoxLayout.Y_AXIS));
        postProcessingContainerPanel.setBorder(new EmptyBorder(0, 50, 0, 50));
        postProcessingContainerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));
        enablePostProcessingCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        enablePostProcessingCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        postProcessingContainerPanel.add(enablePostProcessingCheckBox);
        JCheckBox loadOnStartupCheckBox = new JCheckBox("<html>Activate Post Processing on startup</html>");
        loadOnStartupCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        loadOnStartupCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadOnStartupCheckBox.setVisible(false);
        loadOnStartupCheckBox.addActionListener(e -> {
            if (loadOnStartupCheckBox.isSelected()) {
                configManager.setPostProcessingOnStartup(true);
            } else {
                configManager.setPostProcessingOnStartup(false);
            }
        });
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));
        postProcessingContainerPanel.add(loadOnStartupCheckBox);
        JPanel cardPanel = new JPanel(new CardLayout());
        cardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel postProcessingSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        postProcessingSelectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel selectLabel = new JLabel("Select Post-Processing:");
        selectLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        postProcessingSelectionPanel.add(selectLabel);
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));
        postProcessingSelectComboBox = new JComboBox<>();
        populatePostProcessingComboBox();
        postProcessingSelectComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        postProcessingSelectComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                PostProcessingItem selectedItem = (PostProcessingItem) e.getItem();
                if (selectedItem != null) {
                    configManager.setLastUsedPostProcessingUUID(selectedItem.uuid);
                }
            }
        });
        postProcessingSelectionPanel.add(postProcessingSelectComboBox);
        JPanel placeholderPanel = new JPanel();
        placeholderPanel.setPreferredSize(postProcessingSelectionPanel.getPreferredSize());
        placeholderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardPanel.add(placeholderPanel, "none");
        cardPanel.add(postProcessingSelectionPanel, "active");
        CardLayout cl = (CardLayout) cardPanel.getLayout();
        cl.show(cardPanel, "none");
        postProcessingContainerPanel.add(cardPanel);
        processedText.setLineWrap(true);
        processedText.setWrapStyleWord(true);
        processedText.setRows(3);
        processedText.setMinimumSize(new Dimension(Integer.MAX_VALUE, processedText.getPreferredSize().height));
        processedText.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane processedTextScrollPane = new JScrollPane(processedText);
        processedTextScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        processedTextScrollPane.setMinimumSize(new Dimension(600, processedText.getPreferredSize().height + 10));
        processedTextScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        processedTextScrollPane.setVisible(false);
        JLabel additionalTextLabel = new JLabel("Post Processed text:");
        additionalTextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        additionalTextLabel.setVisible(false);
        postProcessingContainerPanel.add(additionalTextLabel);
        postProcessingContainerPanel.add(processedTextScrollPane);
        JButton copyProcessedTextButton = new JButton("Copy");
        JPanel copyButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        copyButtonPanel.add(copyProcessedTextButton);
        copyButtonPanel.setVisible(false);
        copyButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyProcessedTextButton.setVisible(true);
        copyProcessedTextButton.addActionListener(e -> copyTranscriptionToClipboard(processedText.getText()));
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));
        postProcessingContainerPanel.add(copyButtonPanel);
        enablePostProcessingCheckBox.addActionListener(e -> {
            boolean selected = enablePostProcessingCheckBox.isSelected();
            if (selected) {
                cl.show(cardPanel, "active");
            } else {
                cl.show(cardPanel, "none");
            }
            additionalTextLabel.setVisible(selected);
            loadOnStartupCheckBox.setVisible(selected);
            processedTextScrollPane.setVisible(selected);
            copyButtonPanel.setVisible(selected);
            postProcessingContainerPanel.revalidate();
            postProcessingContainerPanel.repaint();
        });
        if (configManager.isPostProcessingOnStartup()) {
            loadOnStartupCheckBox.setSelected(true);
            enablePostProcessingCheckBox.doClick();
        }
        checkSettings();
        // ===== Add panels to main layout in proper order =====
        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(centerPanel)
                        .addComponent(postProcessingContainerPanel)
                        .addComponent(advancedSettingsContainerPanel)
        );
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addContainerGap() // Top container gap
                        .addComponent(centerPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED) // minimal gap between center and post processing
                        .addComponent(postProcessingContainerPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0) // no gap between post processing and advanced settings
                        .addComponent(advancedSettingsContainerPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap() // Bottom container gap
        );
    }

    private static class PostProcessingItem {
        private final String title;
        private final String uuid;

        public PostProcessingItem(String title, String uuid) {
            this.title = title;
            this.uuid = uuid;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private void populatePostProcessingComboBox() {
        postProcessingSelectComboBox.removeAllItems();
        postProcessingJSONList = configManager.getPostProcessingDataList();
        String lastUsedPostProcessingUUID = configManager.getLastUsedPostProcessingUUID();
        Integer lastUsedIndex = null;
        for (int index = 0; index < postProcessingJSONList.size(); index++) {
            PostProcessingData data = postProcessingJSONList.get(index);
            PostProcessingItem item = new PostProcessingItem(data.title, data.uuid);
            if (data.uuid.equals(lastUsedPostProcessingUUID)) {
                lastUsedIndex = index;
            }
            postProcessingSelectComboBox.addItem(item);
        }
        if (lastUsedIndex != null) {
            postProcessingSelectComboBox.setSelectedIndex(lastUsedIndex);
        }
    }

    private boolean isToggleInProgress = false;

    public void toggleRecording() {
        if (isToggleInProgress || isStoppingInProgress) {
            logger.info("Toggle in progress or stopping in progress. Ignoring.");
            return;
        }
        if (!isRecording) {
            if (!checkSettings()) return;

            // Determine selected recording mode via radio buttons.
            boolean recordMic = false;
            boolean recordSpeaker = false;
            if (recordMicOnlyRadioButton.isSelected()) {
                recordMic = true;
                recordSpeaker = false;
            } else if (recordMicAndAudioRadioButton.isSelected()) {
                recordMic = true;
                recordSpeaker = true;
            } else if (recordAudioOnlyRadioButton.isSelected()) {
                recordMic = false;
                recordSpeaker = true;
            }

            // Start microphone recording if selected
            if (recordMic) {
                startRecording();
            } else {
                lastRecordedMicFile = null;
                logger.info("Microphone recording disabled.");
            }
            // Start speaker recording if selected
            if (recordSpeaker) {
                String selectedDevice = (String) outputDeviceComboBox.getSelectedItem();
                if (selectedDevice != null && !selectedDevice.equals("No monitor devices found")) {
                    speakerWorker = speakerRecorder.toggleRecordButton(selectedDevice, outputProgressBar);
                    if (speakerWorker != null) {
                        speakerWorker.addPropertyChangeListener(evt -> {
                            if ("state".equals(evt.getPropertyName()) &&
                                    evt.getNewValue() == SwingWorker.StateValue.DONE) {
                                try {
                                    lastRecordedSpeakerFile = speakerWorker.get();
                                    // Reset speakerWorker to signal completion
                                    speakerWorker = null;
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        });
                    }
                }
            } else {
                lastRecordedSpeakerFile = null;
                logger.info("Speaker recording disabled.");
            }
            updateUIForRecordingStart();
            updateTrayMenu();
            isRecording = true;
        } else {
            // When toggling off, stop speaker recording if applicable.
            if (recordMicAndAudioRadioButton.isSelected() || recordAudioOnlyRadioButton.isSelected()) {
                speakerRecorder.stopRecordingOutput();
            }
            stopRecording(false);
        }
    }

    private void startRecording() {
        try {
            isRecording = true;
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File audioFile = new File(System.getProperty("java.io.tmpdir"), "record_" + timeStamp + ".wav");
            logger.info("Recording audio file to: " + audioFile.getPath());
            lastRecordedMicFile = audioFile;
            recorder = new AudioRecorder(audioFile, configManager);
            new Thread(recorder::start).start();
            logger.info("Recording started: " + audioFile.getPath());
            recordButton.setText("Stop Recording");
        } catch (Exception e) {
            logger.error("An error occurred while starting the recording", e);
            isRecording = false;
        }
    }

    private boolean isStoppingInProgress = false;

    public void stopRecording(boolean cancelledRecording) {
        updateUIForRecordingStop();
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        if (recorder != null) {
            recorder.stop();
        }
        logger.info("Recording stopped");
        if (!cancelledRecording) {
            // If speaker recording was in use, ensure it has finished.
            if ((recordMicAndAudioRadioButton.isSelected() || recordAudioOnlyRadioButton.isSelected())) {
                if (speakerWorker == null) {
                    // Proceed directly to transcription.
                    transcribe();
                } else if (speakerWorker.getState() != SwingWorker.StateValue.DONE) {
                    // Add a listener to run the mixing process after speakerWorker completes.
                    speakerWorker.addPropertyChangeListener(evt -> {
                        if ("state".equals(evt.getPropertyName()) &&
                                evt.getNewValue() == SwingWorker.StateValue.DONE) {
                            try {
                                if (speakerWorker != null) {
                                    lastRecordedSpeakerFile = speakerWorker.get();
                                }
                            } catch (Exception ex) {
                                logger.error("Error after speaker recording completion", ex);
                            } finally {
                                transcribe();
                            }
                        }
                    });
                } else {
                    transcribe();
                }
            } else {
                transcribe();
            }
        } else {
            logger.info("Recording cancelled");
            updateTrayMenu();
        }
    }

    private void transcribe() {
        try {
            new AudioTranscriptionWorker(lastRecordedMicFile, lastRecordedSpeakerFile).execute();
        } catch (Exception ex) {
            logger.error("Error while mixing audio files", ex);
        }
    }

    public void customUpload(File audioFile) {
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
        lastRecordedSpeakerFile = audioFile;
        transcribe();
    }

    public void playClickSound() {
        if (configManager.isStopSoundEnabled()) {
            new Thread(() -> {
                try {
                    InputStream audioSrc = getClass().getResourceAsStream("/stop.wav");
                    InputStream bufferedIn = new BufferedInputStream(audioSrc);
                    AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioStream);
                    clip.start();
                } catch (Exception e) {
                    logger.error(e);
                }
            }).start();
        }
    }

    private void copyTranscriptionToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

    private void pasteFromClipboard() {
        if (!configManager.isAutoPasteEnabled()) {
            return;
        }
        try {
            Robot robot = new Robot();
            robot.delay(500);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } catch (AWTException e) {
            logger.error("An error occurred while pasting from clipboard", e);
        }
    }

    private void updateUIForRecordingStart() {
        processedText.setFocusable(false);
        processedText.setFocusable(true);
        transcriptionTextArea.setFocusable(false);
        transcriptionTextArea.setFocusable(true);
        int iconSize = UIScale.scale(baseIconSize);
        recordingLabel.setIcon(new FlatSVGIcon("antenna.svg", iconSize, iconSize));
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                isToggleInProgress = true;
                recordingLabel.setEnabled(false);
                recordButton.setEnabled(false);
                Thread.sleep(1000);
                return null;
            }

            @Override
            protected void done() {
                isToggleInProgress = false;
                recordingLabel.setEnabled(true);
                recordButton.setEnabled(true);
                recordButton.setText("Stop Recording");
            }
        };
        worker.execute();
        if ((recordMicAndAudioRadioButton.isSelected() || recordAudioOnlyRadioButton.isSelected())) {
            outputProgressBar.setIndeterminate(true);
            outputProgressBar.setString("Recording...");
        }
    }

    private void updateUIForRecordingStop() {
        int iconSize = UIScale.scale(baseIconSize);
        FlatSVGIcon svgIcon = new FlatSVGIcon("hourglas.svg", iconSize, iconSize);
        recordingLabel.setIcon(svgIcon);
        recordingLabel.setEnabled(false);
        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);

        // Update tray icon for converting status
        TrayIconManager manager = AudioRecorderUI.getTrayIconManager();
        if (manager != null) {
            manager.updateTrayForConverting();
        }

        if ((recordMicAndAudioRadioButton.isSelected() || recordAudioOnlyRadioButton.isSelected())) {
            outputProgressBar.setIndeterminate(false);
            outputProgressBar.setString("Idle");
        }
    }

    private void resetUIAfterTranscription() {
        isStoppingInProgress = false;
        int iconSize = UIScale.scale(baseIconSize);
        FlatSVGIcon svgIcon = new FlatSVGIcon("microphone.svg", iconSize, iconSize);
        recordingLabel.setIcon(svgIcon);
        recordingLabel.setEnabled(true);
        recordButton.setText("Start Recording");
        recordButton.setEnabled(true);
    }

    private boolean checkSettings() {
        boolean settingsSet = true;
        if ((configManager.getWhisperServer().equals("OpenAI") || configManager.getWhisperServer().isEmpty()) && (configManager.getApiKey() == null || configManager.getApiKey().length() == 0)) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                    "API Key must be set in options.");
            settingsSet = false;
        }
        if (configManager.getMicrophone() == null || configManager.getMicrophone().length() == 0) {
            Notificationmanager.getInstance().showNotification(ToastNotification.Type.WARNING,
                    "Microphone must be set in options.");
            settingsSet = false;
        }
        return settingsSet;
    }

    private void updateTrayMenu() {
        TrayIconManager manager = AudioRecorderUI.getTrayIconManager();
        if (manager != null) {
            manager.updateTrayMenu(isRecording);
        }
    }

    private class AudioTranscriptionWorker extends SwingWorker<String, Void> {
        private final File micFile;
        private final File speakerFile;

        public AudioTranscriptionWorker(File micFile, File speakerFile) {
            this.micFile = micFile;
            this.speakerFile = speakerFile;
        }

        /**
         * Processes an audio file by splitting it, transcribing each part using the provided whisperClient,
         * and merging the transcript words (with appropriate cumulative offsets) into one list.
         *
         * @param audioFile      The audio File to process.
         * @param maxSizeBytes   The maximum file size (in bytes) allowed for each split part.
         * @param whisperClient  The instance of WhisperClient used for transcription.
         * @return A merged list of WordTimestampMerger.Word with adjusted timestamps.
         * @throws Exception if processing or transcription fails.
         */
        public List<WordTimestampMerger.Word> processAudioFile(File audioFile, long maxSizeBytes, WhisperClient whisperClient) throws Exception {
            if(audioFile == null) {
                return Collections.emptyList();
            }
            // Create output directory in temp with a unique timestamp based on the file name
            File outputDir = new File(System.getProperty("java.io.tmpdir"), audioFile.getName() + "_" + System.currentTimeMillis());

            // Split the audio file into parts along with cumulative offsets.
            AudioMixer.SplitResult splitResult = AudioMixer.splitFileWithOffsets(audioFile, maxSizeBytes, outputDir);
            List<WordTimestampMerger.Word> mergedWords = new ArrayList<>();

            if(splitResult == null) {
                return Collections.emptyList();
            }
            // Process each split part.
            for (int i = 0; i < splitResult.parts.size(); i++) {
                File partFile = splitResult.parts.get(i);
                long offset = splitResult.offsets.get(i);

                // Transcribe the current part using the whisper client.
                String transcriptText = whisperClient.transcribe(partFile);

                // Parse the transcript JSON with punctuation and case preserved.
                List<WordTimestampMerger.Word> words = WordTimestampMerger.parseJsonPreserve(transcriptText);

                // If not the first part, adjust word timestamps by adding the cumulative offset.
                if (offset != 0) {
                    words = WordTimestampMerger.addOffsetToWords(words, offset);
                }

                // Add the words from the current part into the merged list.
                mergedWords.addAll(words);
            }

            return mergedWords;
        }

        @Override
        protected String doInBackground() {
            try {
                if (configManager.getWhisperServer().equals("OpenAI") ||
                        configManager.getWhisperServer().equals("Faster-Whisper") ||
                        configManager.getWhisperServer().equals("Open WebUI") ||
                        configManager.getWhisperServer().equals("LiteLLM")) {
                    logger.info("Transcribing audio using " + configManager.getWhisperServer());
                    String micText = "";
                    String speakerText = "";
                    if (configManager.getWhisperServer().equals("OpenAI")) {
                        // File file = new File(System.getProperty("java.io.tmpdir"), "merged.wav");
                        //AudioMixer.mergeAudioFiles(micFile, speakerFile, file);
//                        micText = whisperClient.transcribe(file);
//                        return micText;
                        long maxSizeBytes = 24 * 1024 * 1024;
                        List<WordTimestampMerger.Word> mergedMicWords = processAudioFile(micFile, maxSizeBytes, openAITranscribeClient);
                        List<WordTimestampMerger.Word> mergedAudioWords = processAudioFile(speakerFile, maxSizeBytes, openAITranscribeClient);

                        List<WordTimestampMerger.Word> mergedWords = WordTimestampMerger.mergeWords(mergedMicWords, mergedAudioWords);
                        return mergedWords.stream().map(subtitleBlock -> subtitleBlock.word).reduce("", (a, b) -> a + " " + b);

                    } else if (configManager.getWhisperServer().equals("Faster-Whisper")) {
                        long maxSizeBytes = 24 * 1024 * 1024;
                        List<WordTimestampMerger.Word> mergedMicWords = processAudioFile(micFile, maxSizeBytes, fasterWhisperTranscribeClient);
                        List<WordTimestampMerger.Word> mergedAudioWords = processAudioFile(speakerFile, maxSizeBytes, fasterWhisperTranscribeClient);

                        List<WordTimestampMerger.Word> mergedWords = WordTimestampMerger.mergeWords(mergedMicWords, mergedAudioWords);
                        return mergedWords.stream().map(subtitleBlock -> subtitleBlock.word).reduce("", (a, b) -> a + " " + b);
                    } else if (configManager.getWhisperServer().equals("Open WebUI")) {
                        micText = openWebUITranscribeClient.transcribeAudio(micFile);
                        speakerText = openWebUITranscribeClient.transcribeAudio(speakerFile); //not supported due to missing word timestamps

                        return micText;
                    } else {
                        micText = liteLLMRecordingClient.transcribeAudio(micFile);
                        speakerText = liteLLMRecordingClient.transcribeAudio(speakerFile); // not supported due to missing word timestamps
                        return micText;
                    }
//                    List<SrtMerger.SubtitleBlock> micSubTitleBlocks = SrtMerger.parseSrt(micText);
//                    List<SrtMerger.SubtitleBlock> speakerSubTitleBlocks = SrtMerger.parseSrt(speakerText);
//                    List<SrtMerger.SubtitleBlock> subtitleBlocks = SrtMerger.mergeSrtBlocks(micSubTitleBlocks, speakerSubTitleBlocks, 0l);
//                    return subtitleBlocks.stream().map(subtitleBlock -> subtitleBlock.text).reduce("", (a, b) -> a + b);
                } else {
                    logger.error("Unknown Whisper server: " + configManager.getWhisperServer());
                    Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                            "Unknown Whisper server: " + configManager.getWhisperServer());
                    return null;
                }
            } catch (Exception e) {
                logger.error("Error during transcription", e);
                Notificationmanager.getInstance().showNotification(ToastNotification.Type.ERROR,
                        "Error during transcription. See logs.");
                return null;
            }

        }


        @Override
        protected void done() {
            String transcript = null;
            try {
                transcript = get();
                if (transcript != null) {
                    logger.info("Transcribed text: " + transcript);
                    transcriptionTextArea.setText(transcript);
                } else {
                    logger.warn("Transcription resulted in null");
                }
            } catch (Exception e) {
                logger.error("An error occurred while finishing the transcription", e);
            } finally {
                resetUIAfterTranscription();
                isRecording = false;
                if (enablePostProcessingCheckBox.isSelected() && postProcessingSelectComboBox.getSelectedItem() != null) {
                    PostProcessingItem selectedItem = (PostProcessingItem) postProcessingSelectComboBox.getSelectedItem();
                    if (selectedItem != null && selectedItem.uuid != null) {
                        Optional<PostProcessingData> first = configManager.getPostProcessingDataList().stream()
                                .filter(p -> p.uuid.equals(selectedItem.uuid))
                                .findFirst();
                        if (first.isPresent()) {
                            PostProcessingData postProcessingData = first.get();
                            PostProcessingService ppService = new PostProcessingService(configManager);
                            SwingWorker<String, Void> worker = ppService.applyPostProcessing(transcript, postProcessingData);
                            worker.addPropertyChangeListener(evt -> {
                                if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == SwingWorker.StateValue.DONE) {
                                    try {
                                        String processedText = worker.get();
                                        RecorderForm.this.processedText.setText(processedText);
                                        playClickSound();
                                        copyTranscriptionToClipboard(processedText);
                                        pasteFromClipboard();
                                        updateTrayMenu();
                                    } catch (Exception ex) {
                                        logger.error("Error during asynchronous post processing:", ex);
                                    }
                                }
                            });
                        } else {
                            logger.error("Post processing data not found for UUID: " + selectedItem.uuid);
                            updateTrayMenu();
                        }
                    }
                } else {
                    playClickSound();
                    copyTranscriptionToClipboard(transcript);
                    pasteFromClipboard();
                    updateTrayMenu();
                }
            }
        }
    }
}