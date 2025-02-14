package org.whispercat.form.other;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import org.whispercat.*;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;

/**
 *
 * @author Raven
 */
public class FormDashboard extends javax.swing.JPanel {


    private  JButton recordButton;
    private  int baseIconSize = 200;
    private  WhisperClient whisperClient;
    private  ConfigManager configManager;
    private boolean isRecording = false;
    private AudioRecorder recorder;
    private final JTextField transcriptionTextField;
    private final JLabel recordingLabel;
    private JButton copyButton;

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(FormDashboard.class);

    private SystemTray systemTray;
    private MenuItem recordToggleMenuItem;

    public FormDashboard(ConfigManager configManager, WhisperClient whisperClient) {
        this.configManager = configManager;
        String hotkey = configManager.getKeyCombination();
        this.whisperClient = whisperClient;



        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(new EmptyBorder(50, 50, 50, 50));

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
        transcriptionPanel.setBackground(Color.WHITE);
        transcriptionTextField = new JTextField();
        transcriptionTextField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        transcriptionTextField.setEditable(false);
        transcriptionPanel.add(transcriptionTextField);

        copyButton = new JButton("Copy");
        copyButton.setToolTipText("Copy transcription to clipboard");
        copyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        copyButton.addActionListener(e -> copyTranscriptionToClipboard());

        //centerPanel.add(recordingStatusLabel);
        //centerPanel.add(Box.createVerticalStrut(10));
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
        centerPanel.add(Box.createVerticalGlue());


//        centerPanel.addWindowListener(new WindowAdapter() {
//            @Override
//            public void windowClosing(WindowEvent e) {
//                int result = JOptionPane.showConfirmDialog(frame,
//                        "Do you really want to exit the application?",
//                        "Confirm Exit", JOptionPane.YES_NO_OPTION);
//                if (result == JOptionPane.YES_OPTION) {
//                    if (systemTray != null) {
//                        systemTray.shutdown(); // dorkbox API: Tray-Icon entfernen
//                    }
//                    System.exit(0);
//                } else {
//                    frame.setVisible(false);
//                }
//            }
//        });

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
                            NotificationManager2.getInstance().showNotification(ToastNotification2.Type.WARNING, "Only .wav and .mp3 files allowed.");
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

        checkSettings();

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);

        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(centerPanel)
        );

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(centerPanel)
                        .addContainerGap(237, Short.MAX_VALUE)
        );

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
        } else {
            stopRecording(false);
        }
        updateTrayMenu();
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
        recordButton.setEnabled(false);
        if (recorder != null) {
            recorder.stop();
            logger.info("Recording stopped");
            if(!cancelledRecording) {
                new FormDashboard.AudioTranscriptionWorker(recorder.getOutputFile()).execute();
            } else {
                logger.info("Recording cancelled");
            }
        }
    }

    public void stopRecording(File audioFile) {
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
        new FormDashboard.AudioTranscriptionWorker(audioFile).execute();
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

    private void copyTranscriptionToClipboard() {
        String text = transcriptionTextField.getText();
        StringSelection stringSelection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

    private void pasteFromClipboard() {
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
            NotificationManager2.getInstance().showNotification(ToastNotification2.Type.WARNING,
                    "API Key must be set in options.");
            settingsSet = false;
        }
        if (configManager.getMicrophone() == null || configManager.getMicrophone().length() == 0) {
            NotificationManager2.getInstance().showNotification(ToastNotification2.Type.WARNING,
                    "Microphone must be set in options.");
            settingsSet = false;
        }
        return settingsSet;
    }


    private void updateTrayMenu() {
        if (recordToggleMenuItem != null) {
            recordToggleMenuItem.setText(isRecording ? "Stop Recording" : "Start Recording");
        }

        try {
            URL imageURL;
            if (isRecording) {
                imageURL = AudioRecorderUI.class.getResource("/whispercat_recording.png");
            } else {
                imageURL = AudioRecorderUI.class.getResource("/whispercat_tray.png");
            }
            if (imageURL != null) {
                Image trayImage = new ImageIcon(imageURL).getImage();
                systemTray.setImage(trayImage);
            } else {
                logger.error("Tray icon image not found: " + (isRecording ? "whispercat_recording.png" : "whispercat_tray.png"));
            }
        } catch (Exception e) {
            logger.error("Could not update tray icon image", e);
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
//                NotificationManager.getInstance().showNotification(frame, ToastNotification.Type.ERROR,
//                        "Error during transcription. See logs.");
                return null;
            }
        }

        @Override
        protected void done() {
            try {
                playClickSound();
                String transcript = get();
                if (transcript != null) {
                    logger.info("Transcribed text: " + transcript);
                    transcriptionTextField.setText(transcript);
                    copyTranscriptionToClipboard();
                    pasteFromClipboard();
                } else {
                    logger.warn("Transcription resulted in null");
                }
            } catch (Exception e) {
                logger.error("An error occurred while finishing the transcription", e);
            } finally {
                resetUIAfterTranscription();
                isRecording = false;
                updateTrayMenu();
            }
        }
    }

}
