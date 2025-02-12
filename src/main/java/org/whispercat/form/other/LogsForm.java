package org.whispercat.form.other;

import org.whispercat.TextAreaAppender;

import javax.swing.JTextArea;
import javax.swing.JScrollPane;

public class LogsForm extends JScrollPane {

    private JTextArea logsTextArea;

    public LogsForm() {
        logsTextArea = new JTextArea();
        logsTextArea.setEditable(false);

        TextAreaAppender.setTextArea(logsTextArea);

        this.setViewportView(logsTextArea);
    }

}