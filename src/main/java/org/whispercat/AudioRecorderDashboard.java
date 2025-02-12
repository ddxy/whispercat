package org.whispercat;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AudioRecorderDashboard {
    private JFrame frame;

    public AudioRecorderDashboard() {
        FlatDarkLaf.setup(); // Aktiviert das Flat-Design im Dunkelmodus.

        // Erstelle das Hauptfenster
        frame = new JFrame("WhisperCat Dashboard");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        frame.setLayout(new BorderLayout());

        // Hauptpanel mit MiG Layout
        JPanel mainPanel = new JPanel(new MigLayout("fill", "[220][grow]", "[]"));

        // 1. Navigation Panel (Seitenleiste)
        JPanel navigationPanel = createNavigationPanel();
        mainPanel.add(navigationPanel, "growy"); // "growy": Vertikales Wachstum

        // 2. Content Panel (Hauptinhalt)
        JPanel contentPanel = createContentPanel();
        mainPanel.add(contentPanel, "grow");

        // Füge das Hauptpanel dem Frame hinzu
        frame.add(mainPanel, BorderLayout.CENTER);

        // Zeige das Dashboard
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // Methode zur Erstellung der Navigation
    private JPanel createNavigationPanel() {
        JPanel panel = new JPanel(new MigLayout("wrap, fill", "[]", "[]"));
        panel.setBackground(new Color(40, 41, 45)); // Dunkler Hintergrund.

        JLabel title = new JLabel("Raven Channel");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, "center, gapbottom 20");

        // Navigationselemente
        createNavButton(panel, "Dashboard", true);
        createNavButton(panel, "Email", false);
        createNavButton(panel, "Chat", false);
        createNavButton(panel, "Calendar", false);
        createNavButton(panel, "Charts", false);
        createNavButton(panel, "Logout", false);

        return panel;
    }

    // Methode zum Hinzufügen eines Navigationsbuttons
    private void createNavButton(JPanel parent, String text, boolean selected) {
        JButton button = new JButton(text);
        button.setForeground(Color.WHITE);
        button.setBackground(selected ? new Color(85, 107, 255) : new Color(40, 41, 45));
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        parent.add(button, "growx");
    }

    // Methode zur Erstellung des Hauptinhaltsbereichs
    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new MigLayout("fill", "", ""));
        panel.setBackground(Color.DARK_GRAY);

        JLabel label = new JLabel("Dashboard");
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Arial", Font.BOLD, 32));
        panel.add(label, "center, wrap");

        // Beispiel-Panel: Aufnahmebereich.
        JPanel recordPanel = createRecordingPanel();
        panel.add(recordPanel, "center, grow, push");

        return panel;
    }

    // Methode zur Erstellung eines Abschnitts mit Aufnahme- und Transkriptionsfunktionen
    private JPanel createRecordingPanel() {
        JPanel panel = new JPanel(new MigLayout("wrap 2", "[grow, fill][grow, fill]", "[]20[]"));
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(BorderFactory.createTitledBorder("Audio Recorder"));

        JButton recordButton = new JButton("Start Recording");
        recordButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: Implementiere Start/Stop-Aufnahme-Logik
                System.out.println("Recording toggled!");
            }
        });
        panel.add(recordButton, "span, growx");

        JLabel transcriptionLabel = new JLabel("Transcription:");
        transcriptionLabel.setForeground(Color.WHITE);
        panel.add(transcriptionLabel);

        JTextArea transcriptionArea = new JTextArea(10, 30);
        transcriptionArea.setEditable(false);
        transcriptionArea.setBackground(Color.LIGHT_GRAY);
        JScrollPane scrollPane = new JScrollPane(transcriptionArea);
        panel.add(scrollPane, "span, grow");

        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AudioRecorderDashboard::new);
    }
}