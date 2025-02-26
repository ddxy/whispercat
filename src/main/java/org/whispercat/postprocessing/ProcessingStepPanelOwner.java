package org.whispercat.postprocessing;

import java.awt.Component;
import java.util.List;

import org.whispercat.ConfigManager;
import org.whispercat.postprocessing.clients.ElevenLabsVoiceClient;

// This interface defines callbacks and getters that allow a ProcessingStepPanel
// to interact with its parent (here, PostProcessingForm) without creating a circular dependency.
public interface ProcessingStepPanelOwner {
    ConfigManager getConfigManager();
    List<String> getOpenWebUIModelNames();
    List<ElevenLabsVoiceClient.VoiceData> getElevenLabsVoices();
    ElevenLabsVoiceClient getElevenLabsClient();
    void scrollToComponent(Component comp);
    void removeProcessingStep(ProcessingStepPanel panel);
}