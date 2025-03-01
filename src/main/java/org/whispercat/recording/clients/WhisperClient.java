package org.whispercat.recording.clients;

import java.io.File;
import java.io.IOException;

public interface WhisperClient {
    String transcribe(File audioFile) throws IOException;
}
