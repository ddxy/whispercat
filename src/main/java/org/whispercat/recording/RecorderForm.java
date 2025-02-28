package org.whispercat.recording;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import org.whispercat.*;
import org.whispercat.postprocessing.PostProcessingData;
import org.whispercat.postprocessing.PostProcessingService;
import org.whispercat.recording.clients.FasterWhisperTranscribeClient;
import org.whispercat.recording.clients.LiteLLMRecordingClient;
import org.whispercat.recording.clients.OpenAITranscribeClient;
import org.whispercat.recording.clients.OpenWebUITranscribeClient;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.AWTException;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class RecorderForm extends javax.swing.JPanel {
    private final JTextArea processedText = new JTextArea(3, 20);
    private final JCheckBox enablePostProcessingCheckBox = new JCheckBox("<html>Enable Post Processing&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</html>");
    private final JButton recordButton;
    private final int baseIconSize = 200;
    private final OpenAITranscribeClient whisperClient;
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


    // New fields for speaker output and microphone control added in Advanced Settings.
    private JCheckBox recordAudioOutputCheckBox;
    private JComboBox<String> outputDeviceComboBox;
    private JButton testOutputButton;
    private JProgressBar outputProgressBar;
    private JCheckBox doNotRecordMicrophoneCheckBox;
    private SpeakerRecorder speakerRecorder;
    // Instance variables for storing recorded files:
    private File lastRecordedMicFile;
    private File lastRecordedSpeakerFile;

    public RecorderForm(ConfigManager configManager) {
        this.configManager = configManager;
        this.whisperClient = new OpenAITranscribeClient(configManager);
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
                // Accept file list flavor
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
        advancedSettingsContainerPanel.setBorder(new EmptyBorder(10, 50, 0, 50));
        advancedSettingsContainerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // New checkbox to enable advanced recording settings
        JCheckBox enableAdvancedSettingsCheckBox = new JCheckBox("Advanced Settings");
        enableAdvancedSettingsCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        enableAdvancedSettingsCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        advancedSettingsContainerPanel.add(enableAdvancedSettingsCheckBox);
        advancedSettingsContainerPanel.add(Box.createVerticalStrut(10));
        // Panel for additional advanced settings similar to postProcessingContainerPanel
        JPanel advancedSettingsPanel = new JPanel();
        advancedSettingsPanel.setLayout(new BoxLayout(advancedSettingsPanel, BoxLayout.Y_AXIS));
        advancedSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox autoPasteCheckBox = new JCheckBox("Paste from clipboard (Ctrl+V)");
        autoPasteCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        autoPasteCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoPasteCheckBox.setSelected(configManager.isAutoPasteEnabled());
        autoPasteCheckBox.addActionListener(e -> {
            configManager.setAutoPasteEnabled(autoPasteCheckBox.isSelected());
        });
        advancedSettingsPanel.add(autoPasteCheckBox);

        // ===== New Advanced-Settings for Audio Output Recording =====
        JPanel speakerRecordingPanel = new JPanel();
        speakerRecordingPanel.setLayout(new BoxLayout(speakerRecordingPanel, BoxLayout.Y_AXIS));
        speakerRecordingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        speakerRecordingPanel.setBorder(BorderFactory.createTitledBorder("Audio Output Recording Settings"));

        recordAudioOutputCheckBox = new JCheckBox("Record Audio Output");
        recordAudioOutputCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        recordAudioOutputCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        speakerRecordingPanel.add(recordAudioOutputCheckBox);
        speakerRecordingPanel.add(Box.createVerticalStrut(5));

        JPanel outputDevicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        outputDevicePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel outputDeviceLabel = new JLabel("Select Monitor Device:");
        outputDevicePanel.add(outputDeviceLabel);

        outputDeviceComboBox = new JComboBox<>();
        String[] monitorDevices = SpeakerRecorder.getMonitorDevices();
        if (monitorDevices.length == 0) {
            outputDeviceComboBox.addItem("No monitor devices found");
        } else {
            for (String device : monitorDevices) {
                outputDeviceComboBox.addItem(device);
            }
        }
        outputDevicePanel.add(outputDeviceComboBox);

        testOutputButton = new JButton("Test Monitor");
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

        doNotRecordMicrophoneCheckBox = new JCheckBox("Do Not Record Microphone");
        doNotRecordMicrophoneCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        doNotRecordMicrophoneCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        speakerRecordingPanel.add(Box.createVerticalStrut(5));
        speakerRecordingPanel.add(doNotRecordMicrophoneCheckBox);
        // ===== End of new Audio Output Recording settings =====

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
        postProcessingContainerPanel.setBorder(new EmptyBorder(10, 50, 0, 50));
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
            enableAdvancedSettingsCheckBox.doClick();
        }
        checkSettings();

        // ===== Add panels to main layout in proper order =====
        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(centerPanel)
                        .addComponent(advancedSettingsContainerPanel)
                        .addComponent(postProcessingContainerPanel)
        );
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(centerPanel)
                        .addComponent(advancedSettingsContainerPanel)
                        .addComponent(postProcessingContainerPanel)
                        .addContainerGap()
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

            // Start microphone recording if not disabled
            if (!doNotRecordMicrophoneCheckBox.isSelected()) {
                startRecording();
            } else {
                lastRecordedMicFile = null;
                logger.info("Microphone recording disabled.");
            }
            // Start speaker recording if enabled
            if (recordAudioOutputCheckBox.isSelected()) {
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
            if (recordAudioOutputCheckBox.isSelected()) {
                speakerRecorder.stopRecordingOutput();
            }
            customUpload(false);
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

    public void customUpload(boolean cancelledRecording) {
        updateUIForRecordingStop();
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        if (recorder != null) {
            recorder.stop();
        }
        logger.info("Recording stopped");
        if (!cancelledRecording) {
            if (recordAudioOutputCheckBox.isSelected()) {
                // Wenn speakerWorker null ist, wurde entweder keine Speaker-Aufnahme gestartet oder er ist bereits fertig.
                if (speakerWorker == null) {
                    // Direkt zum Mischen übergehen.
                    transcribe();
                } else if (speakerWorker.getState() != SwingWorker.StateValue.DONE) {
                    // Füge einen Listener hinzu, der nach Abschluss des speakerWorker den Mix-Vorgang ausführt.
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
        if (recordAudioOutputCheckBox.isSelected()) {
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
        if (recordAudioOutputCheckBox.isSelected()) {
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
        if (configManager.getApiKey() == null || configManager.getApiKey().length() == 0) {
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
        @Override
        protected String doInBackground() {
            try {
                if (configManager.getWhisperServer().equals("OpenAI")) {
                    logger.info("Transcribing audio using OpenAI");
                    String micText = whisperClient.transcribe(micFile);
                    String speakerText = whisperClient.transcribe(speakerFile);

                    List<SrtMerger.SubtitleBlock> micSubTitleBlocks = SrtMerger.parseSrt(micText);
                    List<SrtMerger.SubtitleBlock> speakerSubTitleBlocks = SrtMerger.parseSrt(speakerText);
                    List<SrtMerger.SubtitleBlock> subtitleBlocks = SrtMerger.mergeSrtBlocks(micSubTitleBlocks, speakerSubTitleBlocks, 0l);
                    return subtitleBlocks.stream().map(subtitleBlock -> subtitleBlock.text).reduce("", (a, b) -> a + b);

                } else if (configManager.getWhisperServer().equals("Faster-Whisper")) {
                    logger.info("Transcribing audio using Faster-Whisper");
                    return fasterWhisperTranscribeClient.transcribe(micFile);
                } else if (configManager.getWhisperServer().equals("Open WebUI")) {
                    logger.info("Transcribing audio using Open WebUI");
                    return openWebUITranscribeClient.transcribeAudio(micFile);
                } else if (configManager.getWhisperServer().equals("LiteLLM")) {
                    logger.info("Transcribing audio using LiteLLM");
                    return liteLLMRecordingClient.transcribeAudio(micFile);
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

    /**
     * Combines two mono audio files into one stereo file.
     * The left channel (channel 1) will contain the samples from micFile
     * and the right channel (channel 2) will contain the samples from speakerFile.
     * If one file is shorter than the other, the missing samples are padded with zero.
     *
     * This implementation assumes 8-bit PCM audio.
     *
     * @param micFile     The microphone recorded file (mono).
     * @param speakerFile The speaker recorded file (mono).
     * @return The output stereo File.
     * @throws Exception if an error occurs during processing.
     */
    private File mixAudioFiles(File micFile, File speakerFile) throws Exception {
        // Falls eine Datei null ist, verwende die andere und erstelle daraus ein Stereo-Format (Kopie in beiden Kanälen).
        if(micFile == null) {
            return speakerFile;
        } else if(speakerFile == null) {
            return micFile;
        }

        // Erstelle den Output-Dateinamen
        File outputFile = new File(System.getProperty("java.io.tmpdir"), "stereo_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".wav");

        // Öffne beide Audio-Streams
        AudioInputStream stream1 = AudioSystem.getAudioInputStream(micFile);
        AudioInputStream stream2 = AudioSystem.getAudioInputStream(speakerFile);

        AudioFormat format1 = stream1.getFormat();
        AudioFormat format2 = stream2.getFormat();

        // Falls die Formate nicht übereinstimmen, versuche stream2 in das Format von stream1 zu konvertieren.
        if (!format1.matches(format2)) {
            stream2 = AudioSystem.getAudioInputStream(format1, stream2);
        }

        // Wir gehen davon aus, dass die Input-Dateien mono sind.
        // Erzeuge ein neues AudioFormat für Stereo (2 Kanäle).
        // Beispiel für 8-bit PCM: SampleSizeInBits = format1.getSampleSizeInBits(), channels = 2.
        AudioFormat stereoFormat = new AudioFormat(
                format1.getEncoding(),
                format1.getSampleRate(),
                format1.getSampleSizeInBits(),
                2,  // 2 Kanäle
                (format1.getSampleSizeInBits() / 8) * 2, // frameSize = bytes per sample * 2 channels
                format1.getFrameRate(),
                format1.isBigEndian()
        );

        // Wir lesen die Dateien blockweise. Für 8-bit PCM ist ein Sample 1 Byte.
        int bytesPerSample = format1.getSampleSizeInBits() / 8;
        byte[] buffer1 = new byte[1024];
        byte[] buffer2 = new byte[1024];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int read1 = 0, read2 = 0;
        // Solange mindestens ein Stream Daten liefert, werden Samples interleaved.
        // Wir gehen zeilenweise (Byte für Byte) vor.
        while (true) {
            read1 = stream1.read(buffer1);
            read2 = stream2.read(buffer2);

            // Falls beide Streams das Ende erreicht haben, brechen wir ab
            if (read1 == -1 && read2 == -1) {
                break;
            }

            // Länge des aktuellen Blocks: wir müssen den maximal gelesenen Wert verwenden
            int length = Math.max(read1 == -1 ? 0 : read1, read2 == -1 ? 0 : read2);

            // Erstelle einen Buffer für den Stereo-Frame (Interleaved: sample1, sample2, sample1, sample2,...)
            byte[] stereoBuffer = new byte[length * 2];  // 2 Kanäle

            for (int i = 0; i < length; i++) {
                // Lese Sample vom ersten Stream, falls vorhanden – sonst 0.
                byte sample1 = (i < (read1 == -1 ? 0 : read1)) ? buffer1[i] : 0;
                // Lese Sample vom zweiten Stream, falls vorhanden – sonst 0.
                byte sample2 = (i < (read2 == -1 ? 0 : read2)) ? buffer2[i] : 0;

                // Im Stereo-Array: zuerst sample1 (linker Kanal), dann sample2 (rechter Kanal)
                stereoBuffer[i * 2] = sample1;
                stereoBuffer[i * 2 + 1] = sample2;
            }

            baos.write(stereoBuffer);
        }

        byte[] stereoBytes = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(stereoBytes);
        AudioInputStream stereoStream = new AudioInputStream(bais, stereoFormat, stereoBytes.length / stereoFormat.getFrameSize());

        // Schreibe die Stereo-Datei als WAV
        AudioSystem.write(stereoStream, AudioFileFormat.Type.WAVE, outputFile);

        stream1.close();
        stream2.close();
        stereoStream.close();

        return outputFile;
    }
}