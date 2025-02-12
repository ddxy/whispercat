package org.whispercat;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import org.whispercat.form.MainForm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;

public class Application extends javax.swing.JFrame {

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(AudioRecorderUI.class);


    private static Application app;
    private final MainForm mainForm;
    private static SystemTray systemTray;

    public Application() {
        initComponents();
        setSize(new Dimension(1366, 768));
        setLocationRelativeTo(null);
        mainForm = new MainForm();
        setContentPane(mainForm);
        getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
    }

    public static void showForm(Component component) {
        component.applyComponentOrientation(app.getComponentOrientation());
        app.mainForm.showForm(component);
    }

    public static void setSelectedMenu(int index, int subIndex) {
        app.mainForm.setSelectedMenu(index, subIndex);
    }

    private void initComponents() {
        NotificationManager2.setWindow(this);
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
        createTrayIcon();

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                NotificationManager2.getInstance().updateAllNotifications();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                NotificationManager2.getInstance().updateAllNotifications();
            }
        });
        pack();
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

            systemTray.getMenu().add(new dorkbox.systemTray.MenuItem("Open", e -> {
                SwingUtilities.invokeLater(() -> {
                    app.setVisible(true);
                    app.setExtendedState(JFrame.NORMAL);
                });
            }));
            systemTray.getMenu().add(new Separator());

//            recordToggleMenuItem = new dorkbox.systemTray.MenuItem(isRecording ? "Stop Recording" : "Start Recording", e -> {
//                toggleRecording();
//            });
//            systemTray.getMenu().add(recordToggleMenuItem);
            systemTray.getMenu().add(new Separator());

            systemTray.getMenu().add(new MenuItem("Exit", e -> {
                int result = JOptionPane.showConfirmDialog(app,
                        "Do you really want to exit WhisperCat?",
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

    public static void main(String args[]) {
        FlatRobotoFont.install();
        FlatLaf.registerCustomDefaultsSource("theme");
        UIManager.put("defaultFont", new Font(FlatRobotoFont.FAMILY, Font.PLAIN, 13));

        FlatMacLightLaf.setup();
        java.awt.EventQueue.invokeLater(() -> {
            app = new Application();
//              app.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            app.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    int result = JOptionPane.showConfirmDialog(app,
                            "Are you sure you want to exit? Click No to minimize to the system tray.",
                            "Confirm Exit", JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        if (systemTray != null) {
                            systemTray.shutdown(); // dorkbox API: Tray-Icon entfernen
                        }
                        System.exit(0);
                    } else {
                        app.setVisible(false);
                    }
                }
            });
            app.setVisible(true);
            setSelectedMenu(0, 0);

        });


    }

}
