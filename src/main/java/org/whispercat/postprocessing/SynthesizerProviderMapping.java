package org.whispercat.postprocessing;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Maps synthesizer provider names to their associated voices.
 * For OpenAI, a fixed list of voices is provided.
 * For ElevenLabs, the voices are retrieved from the ProcessingStepPanelOwner.
 */
public class SynthesizerProviderMapping {

    // Static list of voices for OpenAI
    private static final String[] OPENAI_VOICES = {"alloy", "ash", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer"};

    /**
     * Returns the list of voices for the given synthesizer provider.
     *
     * @param provider the synthesizer provider ("OpenAI" or "ElevenLabs")
     * @param owner    the ProcessingStepPanelOwner instance to retrieve dynamic data (for ElevenLabs)
     * @return a List of voice names.
     */
    public static List<String> getVoices(String provider, ProcessingStepPanelOwner owner) {
        if ("OpenAI".equals(provider)) {
            return Arrays.asList(OPENAI_VOICES);
        } else if ("ElevenLabs".equals(provider)) {
            if (owner.getElevenLabsVoices() != null && !owner.getElevenLabsVoices().isEmpty()) {
                List<String> voices = new ArrayList<>();
                for (org.whispercat.postprocessing.clients.ElevenLabsVoiceClient.VoiceData voice : owner.getElevenLabsVoices()) {
                    voices.add(voice.toString());
                }
                return voices;
            } else {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    /**
     * Returns a default voice for the given provider.
     * If storedVoice is provided (not null and not empty) it takes precedence.
     * Otherwise, returns the first available voice from the list for the provider.
     *
     * @param provider    the synthesizer provider name.
     * @param owner       the ProcessingStepPanelOwner instance.
     * @param storedVoice the previously stored voice (may be empty).
     * @return the default voice as a String.
     */
    public static String getDefaultVoice(String provider, ProcessingStepPanelOwner owner, String storedVoice) {
        if (storedVoice != null && !storedVoice.trim().isEmpty()) {
            return storedVoice;
        }
        List<String> voices = getVoices(provider, owner);
        if (!voices.isEmpty()) {
            return voices.get(0);
        }
        return "";
    }
}