package org.whispercat;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import org.whispercat.recording.RecorderForm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class AudioRecorderUI extends javax.swing.JFrame {

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(AudioRecorderUI.class);


    private static AudioRecorderUI app;
    private final MainForm mainForm;
    private static TrayIconManager trayIconManager;


    public AudioRecorderUI() {
        Notificationmanager.setWindow(this);
        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGap(0, 719, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGap(0, 521, Short.MAX_VALUE)
        );


        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                Notificationmanager.getInstance().updateAllNotifications();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                Notificationmanager.getInstance().updateAllNotifications();
            }
        });
        pack();


        setSize(new Dimension(1366, 850));
        setLocationRelativeTo(null);
        mainForm = new MainForm();
        setContentPane(mainForm);
        getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
    }


    public static void setSelectedMenu(int index, int subIndex) {
        app.mainForm.setSelectedMenu(index, subIndex);
    }


    public static void main(String args[]) {
        FlatRobotoFont.install();
        FlatLaf.registerCustomDefaultsSource("theme");
        UIManager.put("defaultFont", new Font(FlatRobotoFont.FAMILY, Font.PLAIN, 13));
        FlatMacLightLaf.setup();
        java.awt.EventQueue.invokeLater(() -> {
            app = new AudioRecorderUI();
//              app.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            app.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    int result = JOptionPane.showConfirmDialog(app,
                            "Are you sure you want to exit? Click No to minimize to the system tray.",
                            "Confirm Exit", JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        trayIconManager.shutdown();
                        System.exit(0);
                    } else {
                        app.setVisible(false);
                    }
                }
            });
            app.setVisible(true);
            setSelectedMenu(0, 0);

            // Create and initialize the TrayIconManager in a separate thread so it doesn't block the UI.
            trayIconManager = new TrayIconManager();
            new Thread(() -> trayIconManager.createTrayIcon(
                    () -> {
                        // Open callback, show the main application.
                        SwingUtilities.invokeLater(() -> {
                            app.setVisible(true);
                            app.setExtendedState(JFrame.NORMAL);
                            app.toFront();
                            app.requestFocus();
                        });
                    },
                    () -> {
                        RecorderForm dashboard = app.mainForm.recorderForm;
                        dashboard.toggleRecording();
                    }
            )).start();
        });

        // Global fix: Add an AWTEventListener to catch components added later
        // and set the unit increment for any scroll bars found.
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof ContainerEvent) {
                ContainerEvent containerEvent = (ContainerEvent) event;
                if (containerEvent.getID() == ContainerEvent.COMPONENT_ADDED) {
                    // When a component is added, recursively set its scroll unit increments.
                    Component child = containerEvent.getChild();
                    setGlobalScrollUnitIncrement(child, 16);
                }
            }
        }, AWTEvent.CONTAINER_EVENT_MASK);
    }

    public static TrayIconManager getTrayIconManager() {
        return trayIconManager;
    }


    /**
     * Recursively sets the unit increment for any JScrollBar found in the component hierarchy.
     *
     * @param comp      The root component.
     * @param increment The desired unit increment in pixels.
     */
    public static void setGlobalScrollUnitIncrement(Component comp, int increment) {
        if (comp instanceof JScrollBar) {
            ((JScrollBar) comp).setUnitIncrement(increment);
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                setGlobalScrollUnitIncrement(child, increment);
            }
        }
    }


}
