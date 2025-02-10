package org.whispercat;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashSet;
import java.util.Set;

public class GlobalHotkeyListener implements NativeKeyListener {
    private static final Logger logger = LogManager.getLogger(GlobalHotkeyListener.class);
    private final AudioRecorderUI ui;
    private final Set<Integer> pressedKeys = new HashSet<>();
    private String[] hotKeyCombination;
    private String[] hotKeySequence;
    private int sequenceIndex = 0;
    private long sequenceStartTime = 0;
    private KeyCombinationTextField keyCombinationTextField;
    private KeySequenceTextField keySequenceTextField;
    private boolean optionsDialogOpen = false;
    private boolean combinationActive = false;

    public GlobalHotkeyListener(AudioRecorderUI ui, String initialKeyCombination, String initialKeySequence) {
        this.ui = ui;
        updateKeyCombination(initialKeyCombination);
        updateKeySequence(initialKeySequence);
        System.setProperty("jnativehook.lib.locator", "org.whispercat.CustomLibraryLocator");
        try {
            java.util.logging.Logger jLogger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
            jLogger.setLevel(java.util.logging.Level.OFF);
            jLogger.setUseParentHandlers(false);
            GlobalScreen.registerNativeHook();
        } catch (Exception e) {
            logger.error("Failed to register native hook", e);
        }
        GlobalScreen.addNativeKeyListener(this);
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (optionsDialogOpen) {
            if (keyCombinationTextField != null) {
                keyCombinationTextField.processKeyPressed(e);
            }
            if (keySequenceTextField != null) {
                keySequenceTextField.processKeyPressed(e);
            }
            return;
        }
        if (pressedKeys.contains(e.getKeyCode())) {
            return;
        }
        pressedKeys.add(e.getKeyCode());
        processRecordingHotkeys(e);
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        logger.debug("Key released: {}", e.getKeyCode());
        pressedKeys.remove(e.getKeyCode());
        if (optionsDialogOpen) {
            if (keyCombinationTextField != null) {
                keyCombinationTextField.processKeyReleased(e);
            }
            if (keySequenceTextField != null) {
                keySequenceTextField.processKeyReleased(e);
            }
            return;
        }
        if (!isKeyCombinationPressed()) {
            combinationActive = false;
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {}

    public void updateKeyCombination(String newCombination) {
        if (newCombination != null && !newCombination.isEmpty()) {
            hotKeyCombination = newCombination.split(",");
        } else {
            hotKeyCombination = null;
        }
    }

    public void updateKeySequence(String newSequence) {
        if (newSequence != null && !newSequence.isEmpty()) {
            hotKeySequence = newSequence.split(",");
            sequenceIndex = 0;
            sequenceStartTime = 0;
        } else {
            hotKeySequence = null;
            sequenceIndex = 0;
            sequenceStartTime = 0;
        }
    }

    private boolean isKeyCombinationPressed() {
        if (hotKeyCombination == null) {
            return false;
        }
        for (String keyName : hotKeyCombination) {
            if (!pressedKeys.contains(Integer.valueOf(keyName.trim()))) {
                logger.debug("Key combination not fully pressed: {}", keyName);
                return false;
            }
        }
        return true;
    }

    private void processRecordingHotkeys(NativeKeyEvent e) {
        if (hotKeyCombination != null && hotKeyCombination.length > 0 && isKeyCombinationPressed()) {
            if (!combinationActive) {
                combinationActive = true;
                logger.info("Key combination pressed, toggling recording");
                ui.toggleRecording();
                pressedKeys.clear();
                sequenceIndex = 0;
                sequenceStartTime = 0;
            }
            return;
        }
        if (hotKeySequence != null && hotKeySequence.length > 0) {
            long currentTime = System.currentTimeMillis();
            if (sequenceIndex > 0 && currentTime - sequenceStartTime > 1000) {
                logger.debug("Key sequence timeout. More than 1 sec passed.");
                sequenceIndex = 0;
                sequenceStartTime = 0;
            }
            try {
                int expectedKeyCode = Integer.valueOf(hotKeySequence[sequenceIndex].trim());
                if (e.getKeyCode() == expectedKeyCode) {
                    if (sequenceIndex == 0) {
                        sequenceStartTime = currentTime;
                    }
                    sequenceIndex++;
                    if (sequenceIndex == hotKeySequence.length) {
                        if (currentTime - sequenceStartTime <= 1000) {
                            logger.info("Key sequence completed, toggling recording");
                            ui.toggleRecording();
                        } else {
                            logger.debug("Key sequence completed, but time limit was reached. Current time limit: 1000ms");
                        }
                        sequenceIndex = 0;
                        sequenceStartTime = 0;
                    }
                } else {
                    sequenceIndex = 0;
                    sequenceStartTime = 0;
                    try {
                        int firstKeyCode = Integer.valueOf(hotKeySequence[0].trim());
                        if (e.getKeyCode() == firstKeyCode) {
                            sequenceIndex = 1;
                            sequenceStartTime = currentTime;
                        }
                    } catch (NumberFormatException nfe) {
                        logger.error("Invalid key code in key sequence", nfe);
                    }
                }
            } catch (NumberFormatException nfe) {
                logger.error("Invalid key code in key sequence", nfe);
                sequenceIndex = 0;
                sequenceStartTime = 0;
            }
        }
    }

    public void setOptionsDialogOpen(boolean open, KeyCombinationTextField keybindField, KeySequenceTextField keySequenceField) {
        this.optionsDialogOpen = open;
        this.keyCombinationTextField = keybindField;
        this.keySequenceTextField = keySequenceField;
        logger.debug("Options dialog open set to: {}", open);
    }
}