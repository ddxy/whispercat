// TrayIconManager.java

package org.whispercat;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class TrayIconManager {

    private SystemTray systemTray;
    private MenuItem recordToggleMenuItem;
    private boolean isRecording;
    private final String trayIconPath = "/whispercat_tray.png";

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(TrayIconManager.class);


    // Create the tray icon. This method can be called from a separate thread.
    public void createTrayIcon(Runnable openAppCallback, Runnable toggleRecordingCallback) {
        try {
            systemTray = SystemTray.get();
            setTrayImage(trayIconPath);


            // Create the "Open" sidemenu item.
            systemTray.getMenu().add(new dorkbox.systemTray.MenuItem("Open", e -> {
                SwingUtilities.invokeLater(openAppCallback);
            }));

            systemTray.getMenu().add(new Separator());

            // Create the record toggle sidemenu item.
            recordToggleMenuItem = new dorkbox.systemTray.MenuItem(isRecording ? "Stop Recording" : "Start Recording", e -> {
                // When clicked, notify the FormDashboard (or its controller) to toggle recording.
                toggleRecordingCallback.run();
            });
            systemTray.getMenu().add(recordToggleMenuItem);

            systemTray.getMenu().add(new Separator());

            // Create the "Exit" sidemenu item.
            systemTray.getMenu().add(new dorkbox.systemTray.MenuItem("Exit", e -> {
                int result = JOptionPane.showConfirmDialog(null, "Do you really want to exit WhisperCat?", "Confirm Exit", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    systemTray.shutdown();
                    System.exit(0);
                }
            }));
        } catch (Exception e) {
            logger.error("Unable to initialize system tray", e);
        }
    }

    // Called when the recording state changes. 
    public void updateTrayMenu(boolean recording) {
        this.isRecording = recording;
        if (recordToggleMenuItem != null) {
            recordToggleMenuItem.setText(isRecording ? "Stop Recording" : "Start Recording");
        }
        // Update the tray icon:
        if (isRecording) {
            String recordingIconPath = "/whispercat_recording.png";
            setTrayImage(recordingIconPath);
        } else {
            setTrayImage(trayIconPath);
        }
    }

    private void setTrayImage(String imagePath) {
        try {
            URL imageURL = TrayIconManager.class.getResource(imagePath);
            if (imageURL != null) {
                Image trayImage = new ImageIcon(imageURL).getImage();
                systemTray.setImage(trayImage);
            } else {
                System.err.println("Tray icon image not found: " + imagePath);
            }
        } catch (Exception e) {
           logger.error("Error setting tray icon image", e);
        }
    }

    public void shutdown() {
        if (systemTray != null) {
            systemTray.shutdown();
        }
    }
}