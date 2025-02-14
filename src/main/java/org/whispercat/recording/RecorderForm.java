package org.whispercat.recording;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import org.whispercat.*;
import org.whispercat.postprocessing.PostProcessingData;
import org.whispercat.postprocessing.PostProcessingService;

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
import java.util.Date;
import java.util.List;
import java.util.Optional;


public class RecorderForm extends javax.swing.JPanel {

    private final  JTextArea processedText = new JTextArea(3, 20);
    private final JCheckBox enablePostProcessingCheckBox = new JCheckBox(    "<html>Enable Post Processing&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</html>");
    private final JButton recordButton;
    private final int baseIconSize = 200;
    private final WhisperClient whisperClient;
    private final ConfigManager configManager;
    private boolean isRecording = false;
    private AudioRecorder recorder;
    private final JTextArea transcriptionTextArea;
    private final JLabel recordingLabel;
    private JButton copyButton;

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(RecorderForm.class);


    private JComboBox<PostProcessingItem> postProcessingSelectComboBox;
    private List<PostProcessingData> postProcessingJSONList;

    public RecorderForm(ConfigManager configManager, WhisperClient whisperClient) {
        this.configManager = configManager;
        this.whisperClient = whisperClient;


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
                        stopRecording(droppedFile);
                        return true;
                    }
                } catch (Exception ex) {
                    logger.error("Error importing dropped file", ex);
                }
                return false;
            }
        });


        JPanel postProcessingContainerPanel = new JPanel();
        postProcessingContainerPanel.setLayout(new BoxLayout(postProcessingContainerPanel, BoxLayout.Y_AXIS));
        postProcessingContainerPanel.setBorder(new EmptyBorder(10, 50, 50, 50));
        postProcessingContainerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // New checkbox to control auto-paste from clipboard
        JCheckBox autoPasteCheckBox = new JCheckBox("Paste from clipboard (Ctrl+V)");
        autoPasteCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        autoPasteCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoPasteCheckBox.setSelected(configManager.isAutoPasteEnabled()); // Reads the value from ConfigManager
        autoPasteCheckBox.addActionListener(e -> {
            configManager.setAutoPasteEnabled(autoPasteCheckBox.isSelected());
        });
        postProcessingContainerPanel.add(autoPasteCheckBox);
        postProcessingContainerPanel.add(Box.createVerticalStrut(10));

        enablePostProcessingCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        enablePostProcessingCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        postProcessingContainerPanel.add(enablePostProcessingCheckBox);

        JCheckBox loadOnStartupCheckBox = new JCheckBox(
                "<html>Activate on startup</html>");
        loadOnStartupCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        loadOnStartupCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadOnStartupCheckBox.setVisible(false); // initially hidden
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
        //processedText.setVisible(false);

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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);

        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(centerPanel)
                        .addComponent(postProcessingContainerPanel)
        );

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(centerPanel)
                        .addComponent(postProcessingContainerPanel)
                        .addContainerGap(237, Short.MAX_VALUE)
        );

    }

    private static class PostProcessingItem {
        private final String title;
        private final String uuid;

        public PostProcessingItem(String title, String uuid) {
            this.title = title;
            this.uuid = uuid;
        }

        // Return only title for display in the ComboBox.
        @Override
        public String toString() {
            return title;
        }
    }

    private void populatePostProcessingComboBox() {
        postProcessingSelectComboBox.removeAllItems();
        // Get the list of JSON strings.
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
            startRecording();
            updateUIForRecordingStart();
            updateTrayMenu();

        } else {
            stopRecording(false);
        }
    }

    private void startRecording() {
        try {
            isRecording = true;
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File audioFile = new File(System.getProperty("java.io.tmpdir"), "record_" + timeStamp + ".wav");
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
        //recordButton.setEnabled(false);
        if (recorder != null) {
            recorder.stop();
            logger.info("Recording stopped");
            if (!cancelledRecording) {
                new RecorderForm.AudioTranscriptionWorker(recorder.getOutputFile()).execute();
            } else {
                logger.info("Recording cancelled");
                updateTrayMenu();
            }
        }
    }

    public void stopRecording(File audioFile) {
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
        new RecorderForm.AudioTranscriptionWorker(audioFile).execute();
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
    }

    private void updateUIForRecordingStop() {
        int iconSize = UIScale.scale(baseIconSize);
        FlatSVGIcon svgIcon = new FlatSVGIcon("hourglas.svg", iconSize, iconSize);
        recordingLabel.setIcon(svgIcon);
        recordingLabel.setEnabled(false);

        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
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
        private final File audioFile;

        public AudioTranscriptionWorker(File audioFile) {
            this.audioFile = audioFile;
        }

        @Override
        protected String doInBackground() {
            try {
                return whisperClient.transcribe(audioFile);
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
                        Optional<PostProcessingData> first = configManager.getPostProcessingDataList().stream().filter(p -> p.uuid.equals(selectedItem.uuid)).findFirst();
                        if (first.isPresent()) {
                            PostProcessingData postProcessingData = first.get();
                            PostProcessingService ppService = new PostProcessingService(configManager);
                            String processedText = ppService.applyPostProcessing(transcript, postProcessingData);
                            RecorderForm.this.processedText.setText(processedText);
                            playClickSound();
                            copyTranscriptionToClipboard(processedText);
                            pasteFromClipboard();
                            updateTrayMenu();

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
