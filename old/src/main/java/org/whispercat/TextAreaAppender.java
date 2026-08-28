package org.whispercat;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@Plugin(name = "TextAreaAppender", category = "Core", elementType = Appender.ELEMENT_TYPE)
public class TextAreaAppender extends AbstractAppender {

    private static final List<String> messageBuffer = new ArrayList<>();
    private static final int MAX_BUFFER_SIZE = 1000;
    private static JTextArea textArea;

    protected TextAreaAppender(String name, Layout<?> layout) {
        super(name, null, layout, false, null);
    }

    public static void setTextArea(JTextArea textArea) {
        TextAreaAppender.textArea = textArea;

        if (textArea != null) {
            for (String message : messageBuffer) {
                appendToTextArea(message);
            }
        }
    }

    private static void appendToTextArea(String message) {
        SwingUtilities.invokeLater(() -> {
            if (textArea != null) {
                textArea.append(message);
                textArea.setCaretPosition(textArea.getDocument().getLength());
            }
        });
    }

    @PluginFactory
    public static TextAreaAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<?> layout
    ) {
        if (name == null) {
            LOGGER.error("Kein Name für TextAreaAppender angegeben");
            return null;
        }
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }
        return new TextAreaAppender(name, layout);
    }

    @Override
    public void append(LogEvent event) {
        final String message = new String(getLayout().toByteArray(event));

        messageBuffer.add(message);

        if (messageBuffer.size() > MAX_BUFFER_SIZE) {
            messageBuffer.remove(0);
        }

        if (textArea != null) {
            appendToTextArea(message);
        }
    }
}