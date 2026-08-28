package org.whispercat;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ToastNotification extends JDialog {
    private final Type type;
    private float opacity = 0f;
    private boolean fadingIn = true;
    private boolean fadingOut = false;
    private long animationStartTime;
    private final long fadeInDuration = 500;
    private final long fadeOutDuration = 500;
    private final long displayTime = 3000;
    public ToastNotification(Window parent, Type type, String message) {
        super(parent);
        this.type = type;
        setUndecorated(true);
        setFocusableWindowState(false);
        initComponents(message);
        setOpacity(opacity);
        pack();

        if (type == Type.ERROR || type == Type.WARNING) {
            bringParentToFront(parent);
        }
    }

    private void initComponents(String message) {
        setLayout(new BorderLayout());
        setAlwaysOnTop(false);
        setBackground(new Color(0, 0, 0, 0));

        JPanel contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                FlatUIUtils.setRenderingHints(g2d);
                int arc = UIScale.scale(4);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2d.dispose();
            }
        };
        contentPanel.setBackground(getBackgroundColor());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));


        contentPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 10);
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;

        JTextArea messageArea = new JTextArea(message);
        messageArea.setWrapStyleWord(true);
        messageArea.setLineWrap(true);
        messageArea.setEditable(false);
        messageArea.setForeground(new Color(50, 50, 50));
        messageArea.setOpaque(false);
        messageArea.setBackground(new Color(0, 0, 0, 0));
        messageArea.setFont(new Font(UIManager.getFont("Label.font").getName(), Font.PLAIN, 15));
        messageArea.setBorder(null);

        messageArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dispose();
            }
        });

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        contentPanel.add(messageArea, gbc);

        contentPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dispose();
            }
        });

        add(contentPanel, BorderLayout.CENTER);
        pack();
        setMinimumSize(new Dimension(300, getMinimumSize().height));
    }

    private void bringParentToFront(Window parent) {
        if (parent != null) {
            if (parent instanceof Frame) {
                Frame frame = (Frame) parent;
                    frame.setState(Frame.NORMAL);
                    frame.setVisible(true);
            }
            parent.toFront();
            parent.requestFocus();
        }
    }

    private Color getBackgroundColor() {
        switch (type) {
            case SUCCESS:
                return new Color(77, 175, 80, 200);
            case INFO:
                return new Color(33, 150, 243, 200);
            case WARNING:
                return new Color(255, 221, 0, 200);
            case ERROR:
            default:
                return new Color(244, 67, 54, 126);
        }
    }

    public void showToast() {
        animationStartTime = System.currentTimeMillis();
        setVisible(true);
    }

    public void updateAnimation() {
        long currentTime = System.currentTimeMillis();
        if (fadingIn) {
            float progress = (currentTime - animationStartTime) / (float) fadeInDuration;
            if (progress >= 1f) {
                progress = 1f;
                fadingIn = false;
                animationStartTime = currentTime;
            }
            opacity = progress;
            setOpacity(opacity);
        } else {
            // For ERROR and WARNING types, do not auto fade out; keep the notification visible.
            if (type == Type.ERROR || type == Type.WARNING) {
                // Do nothing so that the notification remains visible.
            } else {
                if (!fadingOut) {
                    if ((currentTime - animationStartTime) >= displayTime) {
                        fadingOut = true;
                        animationStartTime = currentTime;
                    }
                } else {
                    float progress = (currentTime - animationStartTime) / (float) fadeOutDuration;
                    if (progress >= 1f) {
                        opacity = 0f;
                        setOpacity(opacity);
                        dispose();
                    } else {
                        opacity = 1f - progress;
                        setOpacity(opacity);
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        Notificationmanager.getInstance().removeNotification(this);
    }

    public enum Type {
        SUCCESS, INFO, WARNING, ERROR
    }
}