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


    private final JFrame frame;
    private final JButton recordButton;
    private final int baseIconSize = 200;
    private final WhisperClient whisperClient;
    private final GlobalHotkeyListener2 globalHotkeyListener;
    private final ConfigManager configManager;
    private boolean isRecording = false;
    private AudioRecorder recorder;
    private final JTextField transcriptionTextField;
    private final JLabel recordingLabel;
    private JButton copyButton;

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(FormDashboard.class);

    private SystemTray systemTray;
    private MenuItem recordToggleMenuItem;

    public FormDashboard() {
        configManager = new ConfigManager();
        extractNativeLibraries();
        String hotkey = configManager.getKeyCombination();
        whisperClient = new WhisperClient(configManager);
        globalHotkeyListener = new GlobalHotkeyListener2(this, hotkey, configManager.getKeySequence());

        frame = new JFrame("WhisperCat");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Color.WHITE);

        URL iconURL = AudioRecorderUI.class.getResource("/whispercat.png");
        if (iconURL != null) {
            ImageIcon imgIcon = new ImageIcon(iconURL);
            Image image = imgIcon.getImage();
            frame.setIconImage(image);
        } else {
            logger.error("Icon not found.");
        }

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(frame,
                        "Do you really want to exit the application?",
                        "Confirm Exit", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    if (systemTray != null) {
                        systemTray.shutdown(); // dorkbox API: Tray-Icon entfernen
                    }
                    System.exit(0);
                } else {
                    frame.setVisible(false);
                }
            }
        });

        frame.setTransferHandler(new TransferHandler() {
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
                            NotificationManager.getInstance().showNotification(frame, ToastNotification.Type.WARNING, "Only .wav and .mp3 files allowed.");
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

//        createTrayIcon();

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(Color.WHITE);
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

        frame.add(centerPanel, BorderLayout.CENTER);
        frame.setSize(500, 450);
        frame.setLocationRelativeTo(null);
        frame.setJMenuBar(createMenuBar());
//        frame.setVisible(true);

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
            stopRecording();
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

    private void stopRecording() {
        updateUIForRecordingStop();
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
        if (recorder != null) {
            recorder.stop();
            logger.info("Recording stopped");
            new FormDashboard.AudioTranscriptionWorker(recorder.getOutputFile(), frame).execute();
        }
    }

    public void stopRecording(File audioFile) {
        isStoppingInProgress = true;
        recordButton.setText("Converting. Please wait...");
        recordButton.setEnabled(false);
        new FormDashboard.AudioTranscriptionWorker(audioFile, frame).execute();
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
            NotificationManager.getInstance().showNotification(frame, ToastNotification.Type.WARNING,
                    "API Key must be set in options.");
            settingsSet = false;
        }
        if (configManager.getMicrophone() == null || configManager.getMicrophone().length() == 0) {
            NotificationManager.getInstance().showNotification(frame, ToastNotification.Type.WARNING,
                    "Microphone must be set in options.");
            settingsSet = false;
        }
        return settingsSet;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Menu");
        menuBar.add(menu);

        JMenuItem optionsItem = new JMenuItem("Options");
        optionsItem.addActionListener(e -> openSettings());
        menu.add(optionsItem);


        JMenuItem logsItem = new JMenuItem("Logs");
        logsItem.addActionListener(e -> openLogsWindow());
        menu.add(logsItem);

        JMenuItem uploadFileItem = new JMenuItem("Upload File");
        uploadFileItem.addActionListener(e -> {
            // Open file chooser for audio file upload with a file filter for .wav and .mp3 files.
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Audio Files (WAV, MP3)", "wav", "mp3"));
            int returnVal = fileChooser.showOpenDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                // Check if file extension is ".wav" or ".mp3"
                String lowerName = selectedFile.getName().toLowerCase();
                if (!(lowerName.endsWith(".wav") || lowerName.endsWith(".mp3"))) {
                    NotificationManager.getInstance().showNotification(frame, ToastNotification.Type.WARNING, "Only .wav and .mp3 files allowed.");
                    return;
                }
                // Pass the selected file to the unified stopRecording method
                stopRecording(selectedFile);
            }
        });
        menu.add(uploadFileItem);

        return menuBar;
    }

    private void openSettings() {
        SettingsDialog settingsDialog = new SettingsDialog(frame, configManager);
        globalHotkeyListener.setOptionsDialogOpen(true, settingsDialog.getKeybindTextField(), settingsDialog.getKeySequenceTextField());
        settingsDialog.setVisible(true);
        globalHotkeyListener.setOptionsDialogOpen(false, null, null);
        String newKeyCombination = settingsDialog.getKeybindTextField().getKeysDisplayed().stream()
                .map(String::valueOf)
                .reduce((s1, s2) -> s1 + "," + s2)
                .orElse("");

        globalHotkeyListener.updateKeyCombination(newKeyCombination);

        String keySequenceString = settingsDialog.getKeySequenceTextField().getKeysDisplayed().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        globalHotkeyListener.updateKeySequence(keySequenceString);
    }


    private void openLogsWindow() {
        JFrame logsFrame = new JFrame("Logs");
        logsFrame.setSize(600, 400);
        logsFrame.setLocationRelativeTo(frame);

        JTextArea logsTextArea = new JTextArea();
        logsTextArea.setEditable(false);

        TextAreaAppender.setTextArea(logsTextArea);

        logsFrame.add(new JScrollPane(logsTextArea));
        logsFrame.setVisible(true);
    }

    private void createTrayIcon() {
        try {
            systemTray = SystemTray.get();

            URL iconURL = AudioRecorderUI.class.getResource("/whispercat_tray.png");
            if (iconURL != null) {
                Image icon = (new ImageIcon(iconURL)).getImage();
                systemTray.setImage(icon);
            }

            systemTray.setStatus("WhisperCat");
            systemTray.setTooltip("WhisperCat");

            systemTray.getMenu().add(new MenuItem("Open", e -> {
                SwingUtilities.invokeLater(() -> {
                    frame.setVisible(true);
                    frame.setExtendedState(JFrame.NORMAL);
                });
            }));
            systemTray.getMenu().add(new Separator());

            recordToggleMenuItem = new MenuItem(isRecording ? "Stop Recording" : "Start Recording", e -> {
                toggleRecording();
            });
            systemTray.getMenu().add(recordToggleMenuItem);
            systemTray.getMenu().add(new Separator());

            systemTray.getMenu().add(new MenuItem("Exit", e -> {
                int result = JOptionPane.showConfirmDialog(frame,
                        "Do you really want to exit the application?",
                        "Confirm Exit", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    systemTray.shutdown();
                    System.exit(0);
                }
            }));
        } catch (Exception e) {
            logger.error("Unable to initialize system tray", e);
        }
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

    private void extractNativeLibraries() {
        String[] platforms = {"windows", "linux", "macos"};
        String[] architectures = {"x86", "x86_64"};
        String libName = "JNativeHook";
        String baseDir = configManager.getConfigDirectory();
        for (String platform : platforms) {
            for (String arch : architectures) {
                String libFileName = System.mapLibraryName(libName);
                if (platform.equals("macos")) {
                    libFileName = libFileName.replace(".jnilib", ".dylib");
                }
                String pathInJar = "/native/" + platform + "/" + arch + "/" + libFileName;
                String outputPath = baseDir + "/" + platform + "/" + arch + "/" + libFileName;
                File outputFile = new File(outputPath);
                if (!outputFile.exists()) {
                    outputFile.getParentFile().mkdirs();
                    try (InputStream is = getClass().getResourceAsStream(pathInJar);
                         OutputStream os = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception e) {
                        logger.error("Error extracting native libraries", e);
                    }
                }
            }
        }
    }

    private class AudioTranscriptionWorker extends SwingWorker<String, Void> {
        private final File audioFile;
        private final JFrame mainFrame;

        public AudioTranscriptionWorker(File audioFile, JFrame mainFrame) {
            this.audioFile = audioFile;
            this.mainFrame = mainFrame;
        }

        @Override
        protected String doInBackground() {
            try {
                return whisperClient.transcribe(audioFile);
            } catch (Exception e) {
                logger.error("Error during transcription", e);
                NotificationManager.getInstance().showNotification(frame, ToastNotification.Type.ERROR,
                        "Error during transcription. See logs.");
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
