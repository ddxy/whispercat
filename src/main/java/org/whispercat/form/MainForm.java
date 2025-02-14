package org.whispercat.form;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import org.whispercat.ConfigManager;
import org.whispercat.GlobalHotkeyListener2;
import org.whispercat.WhisperClient;
import org.whispercat.form.other.FormDashboard;
import org.whispercat.form.other.LogsForm;
import org.whispercat.form.other.SettingsForm;
import org.whispercat.menu.Menu;
import org.whispercat.menu.MenuAction;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class MainForm extends JLayeredPane {

    private WhisperClient whisperClient;
    private GlobalHotkeyListener2 globalHotkeyListener;
    private ConfigManager configManager;
    public FormDashboard formDashboard;
    public SettingsForm settingsForm;
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(MainForm.class);

    public MainForm() {
        init();
    }

    private void init() {
        setBorder(new EmptyBorder(5, 5, 5, 5));
        setLayout(new MainFormLayout());
        menu = new Menu();
        panelBody = new JPanel(new BorderLayout());
        initMenuArrowIcon();
        menuButton.putClientProperty(FlatClientProperties.STYLE, ""
                + "background:$Menu.button.background;"
                + "arc:999;"
                + "focusWidth:0;"
                + "borderWidth:0");
        menuButton.addActionListener((ActionEvent e) -> {
            setMenuFull(!menu.isMenuFull());
        });
        initMenuEvent();
        setLayer(menuButton, JLayeredPane.POPUP_LAYER);
        add(menuButton);
        add(menu);
        add(panelBody);

        configManager = new ConfigManager();
        extractNativeLibraries();
        String hotkey = configManager.getKeyCombination();
        whisperClient = new WhisperClient(configManager);
        globalHotkeyListener = new GlobalHotkeyListener2(this, hotkey, configManager.getKeySequence());
    }

    @Override
    public void applyComponentOrientation(ComponentOrientation o) {
        super.applyComponentOrientation(o);
        initMenuArrowIcon();
    }

    private void initMenuArrowIcon() {
        if (menuButton == null) {
            menuButton = new JButton();
        }
        String icon = (getComponentOrientation().isLeftToRight()) ? "menu_left.svg" : "menu_right.svg";
        menuButton.setIcon(new FlatSVGIcon("icon/svg/" + icon, 0.8f));
    }

    private void initMenuEvent() {
        menu.addMenuEvent((int index, int subIndex, MenuAction action) -> {
            globalHotkeyListener.setOptionsDialogOpen(false, null, null);
            globalHotkeyListener.updateKeyCombination(configManager.getKeyCombination());
            globalHotkeyListener.updateKeySequence(configManager.getKeySequence());

            // stop recording only if there is a switch from dashboard to another menu
            if( index != 0 && formDashboard != null) {
                formDashboard.stopRecording(true);
                formDashboard = null;
            }

            if (index == 0 && formDashboard == null) {
                this.formDashboard = new FormDashboard(configManager, whisperClient);
                showForm(formDashboard);
            } else if (index == 1) {
                if (subIndex == 1) {
                    SettingsForm settingsForm1 = new SettingsForm(configManager);
                    showForm(settingsForm1);
                    globalHotkeyListener.setOptionsDialogOpen(true, settingsForm1.getKeybindTextField(), settingsForm1.getKeySequenceTextField());
                } else if (subIndex == 2) {
                    showForm(new LogsForm());
                } else {
                    action.cancel();
                }
            } else if (index == 9) {
            } else {
                action.cancel();
            }
        });
    }

    private void setMenuFull(boolean full) {
        String icon;
        if (getComponentOrientation().isLeftToRight()) {
            icon = (full) ? "menu_left.svg" : "menu_right.svg";
        } else {
            icon = (full) ? "menu_right.svg" : "menu_left.svg";
        }
        menuButton.setIcon(new FlatSVGIcon("icon/svg/" + icon, 0.8f));
        menu.setMenuFull(full);
        revalidate();
    }

    public void hideMenu() {
        menu.hideMenuItem();
    }

    public void showForm(Component component) {
        panelBody.removeAll();
        panelBody.add(component);
        panelBody.repaint();
        panelBody.revalidate();
    }

    public void setSelectedMenu(int index, int subIndex) {
        menu.setSelectedMenu(index, subIndex);
    }

    private Menu menu;
    private JPanel panelBody;
    private JButton menuButton;

    private class MainFormLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                return new Dimension(5, 5);
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                return new Dimension(0, 0);
            }
        }

        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                boolean ltr = parent.getComponentOrientation().isLeftToRight();
                Insets insets = UIScale.scale(parent.getInsets());
                int x = insets.left;
                int y = insets.top;
                int width = parent.getWidth() - (insets.left + insets.right);
                int height = parent.getHeight() - (insets.top + insets.bottom);
                int menuWidth = UIScale.scale(menu.isMenuFull() ? menu.getMenuMaxWidth() : menu.getMenuMinWidth());
                int menuX = ltr ? x : x + width - menuWidth;
                menu.setBounds(menuX, y, menuWidth, height);
                int menuButtonWidth = menuButton.getPreferredSize().width;
                int menuButtonHeight = menuButton.getPreferredSize().height;
                int menubX;
                if (ltr) {
                    menubX = (int) (x + menuWidth - (menuButtonWidth * (menu.isMenuFull() ? 0.5f : 0.3f)));
                } else {
                    menubX = (int) (menuX - (menuButtonWidth * (menu.isMenuFull() ? 0.5f : 0.7f)));
                }
                menuButton.setBounds(menubX, UIScale.scale(30), menuButtonWidth, menuButtonHeight);
                int gap = UIScale.scale(5);
                int bodyWidth = width - menuWidth - gap;
                int bodyHeight = height;
                int bodyx = ltr ? (x + menuWidth + gap) : x;
                int bodyy = y;
                panelBody.setBounds(bodyx, bodyy, bodyWidth, bodyHeight);
            }
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
}
