package org.whispercat;

import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.StateChangeReturn;
import org.freedesktop.gstreamer.Structure;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RecordAudioExample extends JFrame {

    private JComboBox<String> monitorComboBox;  // ComboBox for monitor device selection
    private JButton recordButton;               // Button to trigger recording
    private JButton testButton;                 // Button to test the selected monitor device (feedback test)
    private JButton testSinusButton;            // Button to test a Sinus tone (without audible output)
    private JLabel statusLabel;                 // Label to display status messages
    private JProgressBar levelBar;              // Progress bar to show audio level

    // The current GStreamer pipeline for recording
    private Pipeline pipeline;
    // Indicates if recording is currently active
    private boolean recording = false;
    // File to store the recorded audio
    private File recordedFile;

    public RecordAudioExample() {
        super("Recorder UI");
        initComponents();
    }

    private void initComponents() {
        // Basic frame setup
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(550, 350);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Top panel: Monitor device selection
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        topPanel.add(new JLabel("Select Monitor Device:"));
        String[] monitorDevices = getMonitorDevices();
        if (monitorDevices.length == 0) {
            monitorDevices = new String[]{"No monitor devices found"};
        }
        monitorComboBox = new JComboBox<>(monitorDevices);
        topPanel.add(monitorComboBox);

        // Middle panel: Buttons for Recording, Testing Monitor and Testing Sinus Tone
        JPanel middlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        recordButton = new JButton("Record");
        recordButton.addActionListener(this::recordButtonAction);
        middlePanel.add(recordButton);

        testButton = new JButton("Test Monitor");
        testButton.addActionListener(this::testButtonAction);
        middlePanel.add(testButton);

        testSinusButton = new JButton("Test Sinus");
        testSinusButton.addActionListener(this::testSinusButtonAction);
        middlePanel.add(testSinusButton);

        // Bottom panel: Status label and Level meter (ProgressBar)
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        statusLabel = new JLabel("Ready");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        levelBar = new JProgressBar(0, 100);
        levelBar.setStringPainted(true);
        levelBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        levelBar.setValue(0);
        bottomPanel.add(statusLabel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        bottomPanel.add(new JLabel("Audio Level:"));
        bottomPanel.add(levelBar);

        add(topPanel, BorderLayout.NORTH);
        add(middlePanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Retrieves monitor devices (PulseAudio sources containing "monitor") by running 'pactl list sources'
     *
     * @return An array of monitor device names.
     */
    private String[] getMonitorDevices() {
        List<String> devices = new ArrayList<>();
        ProcessBuilder pb = new ProcessBuilder("pactl", "list", "sources");
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("Name:")) {
                        String deviceName = line.substring("Name:".length()).trim();
                        if (deviceName.toLowerCase().contains("monitor")) {
                            devices.add(deviceName);
                        }
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
        return devices.toArray(new String[0]);
    }

    /**
     * Handles the record button action.
     * Starts or stops the recording by building and controlling a GStreamer pipeline.
     */
    private void recordButtonAction(ActionEvent event) {
        if (!recording) {
            String selectedDevice = (String) monitorComboBox.getSelectedItem();
            if (selectedDevice == null || selectedDevice.trim().isEmpty() ||
                    selectedDevice.equals("No monitor devices found")) {
                JOptionPane.showMessageDialog(this,
                        "No valid monitor device selected!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                recordedFile = File.createTempFile("recordedAudio_", ".wav");
                statusLabel.setText("Recording... File: " + recordedFile.getAbsolutePath());
                recordButton.setText("Stop");
                String pipelineDescription =
                        "pulsesrc device=\"" + selectedDevice + "\" ! " +
                                "audioconvert ! audioresample ! wavenc ! " +
                                "filesink location=" + recordedFile.getAbsolutePath();
                pipeline = (Pipeline) Gst.parseLaunch(pipelineDescription);
                pipeline.play();
                recording = true;
            } catch (IOException ex) {
                ex.printStackTrace();
                statusLabel.setText("Error: " + ex.getMessage());
            }
        } else {
            if (pipeline != null) {
                StateChangeReturn ret = pipeline.setState(State.NULL);
                if (ret != StateChangeReturn.SUCCESS) {
                    System.err.println("Pipeline did not stop successfully: " + ret);
                }
                pipeline.dispose();
                pipeline = null;
            }
            recording = false;
            recordButton.setText("Record");
            statusLabel.setText("Recording stopped. File saved at: " + recordedFile.getAbsolutePath());
        }
    }

    /**
     * Handles the test button action.
     * Tests the selected monitor device pipeline (loopback test) without akustische Wiedergabe.
     */
    private void testButtonAction(ActionEvent event) {
        String selectedDevice = (String) monitorComboBox.getSelectedItem();
        if (selectedDevice == null || selectedDevice.trim().isEmpty() ||
                selectedDevice.equals("No monitor devices found")) {
            JOptionPane.showMessageDialog(this,
                    "No valid monitor device selected!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        testButton.setEnabled(false);
        statusLabel.setText("Testing Monitor: " + selectedDevice);
        levelBar.setValue(0);
        // Verwende fakesink statt autoaudiosink, damit kein Sound ausgegeben wird.
        String testPipelineDescription =
                "pulsesrc device=\"" + selectedDevice + "\" ! " +
                        "level interval=100000000 ! " +  // 100ms zwischen Level-Nachrichten
                        "audioconvert ! audioresample ! fakesink";
        new Thread(() -> {
            Pipeline testPipeline = (Pipeline) Gst.parseLaunch(testPipelineDescription);
            Bus bus = testPipeline.getBus();
            bus.connect((Bus.MESSAGE) (bus1, message) -> {
                Structure struct = message.getStructure();
                if (struct != null && "level".equals(struct.getName()) && struct.hasField("rms")) {
                    double[] rmsValues = struct.getDoubles("rms");
                    double dB = rmsValues[0];
                    int progress = (int) (((dB + 60) / 60) * 100);
                    progress = Math.max(0, Math.min(progress, 100));
                    int finalProgress = progress;
                    SwingUtilities.invokeLater(() -> levelBar.setValue(finalProgress));
                }
            });
            testPipeline.play();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            testPipeline.setState(State.NULL);
            testPipeline.dispose();
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Monitor test finished.");
                testButton.setEnabled(true);
                levelBar.setValue(0);
            });
        }).start();
    }

    /**
     * Handles the "Test Sinus" button action.
     * Spielt einen Sinuston (audiotestsrc) ab, liefert über das Level-Element Pegel-Nachrichten,
     * aber dank fakesink erfolgt keine hörbare Ausgabe.
     */
    private void testSinusButtonAction(ActionEvent event) {
        testSinusButton.setEnabled(false);
        statusLabel.setText("Testing Sinus Tone (no audible output)...");
        levelBar.setValue(0);
        String testPipelineDescription =
                "audiotestsrc wave=sine freq=440 num-buffers=200 ! " +
                        "level interval=100000000 ! " +
                        "audioconvert ! audioresample ! fakesink";
        new Thread(() -> {
            Pipeline testPipeline = (Pipeline) Gst.parseLaunch(testPipelineDescription);
            Bus bus = testPipeline.getBus();
            bus.connect((Bus.MESSAGE) (bus1, message) -> {
                Structure struct = message.getStructure();
                if (struct != null && "level".equals(struct.getName()) && struct.hasField("rms")) {
                    double[] rmsValues = struct.getDoubles("rms");
                    double dB = rmsValues[0];
                    var ref = new Object() {
                        int progress = (int) (((dB + 60) / 60) * 100);
                    };
                    ref.progress = Math.max(0, Math.min(ref.progress, 100));
                    SwingUtilities.invokeLater(() -> levelBar.setValue(ref.progress));
                }
            });
            testPipeline.play();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            testPipeline.setState(State.NULL);
            testPipeline.dispose();
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Sinus test finished.");
                testSinusButton.setEnabled(true);
                levelBar.setValue(0);
            });
        }).start();
    }

    public static void main(String[] args) {
        Gst.init("RecorderUI", args);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // Ignore Look and Feel exceptions
        }
        SwingUtilities.invokeLater(() -> {
            RecordAudioExample frame = new RecordAudioExample();
            frame.setVisible(true);
        });
    }
}