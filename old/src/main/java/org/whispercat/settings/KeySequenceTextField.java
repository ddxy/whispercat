package org.whispercat.settings;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class KeySequenceTextField extends JTextField {
    private final List<Integer> keySequence = new ArrayList<>();

    public KeySequenceTextField() {
        disableDefaultKeyBindings();
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (getText().isEmpty()) {
                    clearSequence();
                }
            }
        });
    }

    private void disableDefaultKeyBindings() {
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control V"), "none");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control C"), "none");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control X"), "none");
    }

    @Override
    protected void processKeyEvent(java.awt.event.KeyEvent e) {
        // Default key bindings are disabled.
        e.consume();
    }

    public void processKeyPressed(NativeKeyEvent e) {
        if (hasFocus()) {
            keySequence.add(e.getKeyCode());
            updateText();
        }
    }

    public void processKeyReleased(NativeKeyEvent e) {
    }

    private void updateText() {
        String sequenceText = keySequence.stream()
                .map(NativeKeyEvent::getKeyText)
                .collect(Collectors.joining(" -> "));
        System.out.println(sequenceText);
        setText(sequenceText);
    }

    public List<Integer> getKeysSequence() {
        return new ArrayList<>(keySequence);
    }

    public List<Integer> getKeysDisplayed() {
        return getKeysSequence();
    }

    public void setKeysSequence(List<Integer> newSequence) {
        keySequence.clear();
        keySequence.addAll(newSequence);
        updateText();
    }

    public void setKeysDisplayed(List<Integer> keysDisplayed) {
        setKeysSequence(keysDisplayed);
    }

    public void clearSequence() {
        keySequence.clear();
        setText("");
    }
}