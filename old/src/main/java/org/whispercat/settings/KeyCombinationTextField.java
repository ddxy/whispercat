package org.whispercat.settings;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
// TODO: Merge later
public class KeyCombinationTextField extends JTextField {
    private final Set<Integer> keysPressed = new HashSet<>();
    private Set<Integer> keysDisplayed = new HashSet<>();

    public KeyCombinationTextField() {
        disableDefaultKeyBindings();
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (getText().isEmpty()) {
                    setText("");
                    keysDisplayed.clear();
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
            keysPressed.add(e.getKeyCode());
            updateText();
        }
    }

    public void processKeyReleased(NativeKeyEvent e) {
        if (hasFocus()) {
            keysPressed.remove(e.getKeyCode());
        }
    }

    private void updateText() {
        String newKeyCombination = keysPressed.stream()
                .map(NativeKeyEvent::getKeyText)
                .sorted()
                .collect(Collectors.joining("+"));
        setText(newKeyCombination);
        keysDisplayed = new HashSet<>(keysPressed);
    }

    public Set<Integer> getKeysDisplayed() {
        return new HashSet<>(keysDisplayed);
    }

    public void setKeysDisplayed(Set<Integer> keysDisplayed) {
        this.keysDisplayed = new HashSet<>(keysDisplayed);
    }
}