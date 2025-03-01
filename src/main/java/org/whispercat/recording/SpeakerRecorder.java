package org.whispercat.recording;

import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.StateChangeReturn;
import org.freedesktop.gstreamer.Structure;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class encapsulates the functionality for recording the audio output (system/speakers)
 * and testing the audio output. It is now implemented in analogy to RecordAudioExample.
 */
public class SpeakerRecorder {

    private boolean isRecordingOutput = false;
    private static final Logger logger = Logger.getLogger(SpeakerRecorder.class.getName());
    private Pipeline pipeline;

    /**
     * Returns the monitor devices (PulseAudio sources that contain "monitor") obtained by running 'pactl list sources'.
     * @return An array of monitor device names.
     */
    public static String[] getMonitorDevices() {
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
     * Tests the selected monitor device by building and running a test GStreamer pipeline.
     * The pipeline uses pulsesrc, level (producing level messages) and fakesink (no audible output).
     * The provided progressBar shows the audio level (including string feedback).
     * @param outputDevice The selected monitor device.
     * @param progressBar The progress bar to update in the UI.
     */
    public void testAudioOutput(String outputDevice, JProgressBar progressBar) {
        logger.info("Testing audio output on device: " + outputDevice);
        SwingUtilities.invokeLater(() -> {
            //progressBar.setIndeterminate(true);
            progressBar.setString("Playing...");
        });

        String testPipelineDescription = "pulsesrc device=\"" + outputDevice + "\" ! " +
                "level interval=100000000 ! audioconvert ! audioresample ! fakesink";

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
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(ref.progress);
                        progressBar.setString("Playing... " + ref.progress + "%");
                    });
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
                progressBar.setIndeterminate(false);
                progressBar.setString("Idle");
                progressBar.setValue(0);
            });
        }).start();
    }

    /**
     * Starts recording audio output asynchronously from the specified monitor device.
     * The recording is performed in a separate SwingWorker thread so that the UI is not blocked.
     * During recording, the provided progressBar displays the status ("Recording...").
     * When the recording is stopped (via stopRecordingOutput()), the SwingWorker ends and returns the recorded File.
     *
     * @param outputDevice The selected monitor device.
     * @param progressBar  The progress bar to update in the UI.
     * @return A SwingWorker&lt;File, Void&gt; which, when finished, returns the recorded audio file.
     */
    public SwingWorker<File, Void> startRecordingOutputAsync(String outputDevice, JProgressBar progressBar) {
        SwingWorker<File, Void> worker = new SwingWorker<File, Void>() {
            File recordingFile = new File(System.getProperty("java.io.tmpdir"),
                    "record_output_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".wav");

            @Override
            protected File doInBackground() throws Exception {
                String pipelineDescription = "pulsesrc device=\"" + outputDevice + "\" ! "
                        + "audioconvert ! audioresample ! audio/x-raw,channels=1 ! wavenc ! filesink location="
                        + recordingFile.getAbsolutePath();
//                String pipelineDescription = "pulsesrc device=\"" + outputDevice + "\" ! " +
//                        "audioconvert ! audioresample ! wavenc ! filesink location=" + recordingFile.getAbsolutePath();
                pipeline = (Pipeline) Gst.parseLaunch(pipelineDescription);
                // Optional: Falls der Ausgangstext oder Level-Meldungen über den Bus eingefangen werden sollen,
                // kann hier der Bus verbunden werden, um die progressBar zu aktualisieren.
                Bus bus = pipeline.getBus();
                bus.connect((Bus.MESSAGE) (bus1, message) -> {
                    // Hier könnte u. a. auf level-Meldungen reagiert und progressBar aktualisiert werden.
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(true);
                        progressBar.setString("Recording...");
                    });
                });
                pipeline.play();
                isRecordingOutput = true;
                // Warte in einer Schleife, bis isRecordingOutput false gesetzt wird (z. B. durch stopRecordingOutput).
                while (isRecordingOutput && !isCancelled()) {
                    Thread.sleep(200);
                }
                // Nach dem Stoppen: Pipeline stoppen und aufräumen.
                if (pipeline != null) {
                    pipeline.setState(State.NULL);
                    pipeline.dispose();
                    pipeline = null;
                }
                return recordingFile;
            }

            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Idle");
                });
            }
        };
        worker.execute();
        return worker;
    }

    /**
     * Stops recording the audio output.
     * This method should be called to signal the asynchronous recording thread to finish.
     */
    public void stopRecordingOutput() {
        if (isRecordingOutput) {
            logger.info("Stopping audio output recording.");
            isRecordingOutput = false; // Signal to exit the recording loop in the SwingWorker
        }
    }

    /**
     * Toggles the recording of audio output based on the current state.
     * This method can be used when the global record button is pressed.
     * In this asynchronous implementation, if recording is not active yet, it starts the SwingWorker.
     * Otherwise, it stops the recording.
     *
     * @param outputDevice The selected monitor device.
     * @param progressBar  The progress bar to update in the UI.
     * @return The SwingWorker<File,Void> that is active or that has completed.
     */
    public SwingWorker<File, Void> toggleRecordButton(String outputDevice, JProgressBar progressBar) {
        // If already recording, stop the asynchronous recording and return null.
        if (isRecordingOutput) {
            stopRecordingOutput();
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setString("Idle");
                progressBar.setValue(0);
            });
            return null;
        } else {
            // Start the asynchronous recording and return the SwingWorker.
            return startRecordingOutputAsync(outputDevice, progressBar);
        }
    }
}