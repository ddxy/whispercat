package org.whispercat.postprocessing;

/**
 * Data model class for a single processing step.
 */
public class ProcessingStepData {
    /**
     * Possible values for type:
     * - "Prompt"
     * - "Text Replacement"
     * - "TextToSpeech"
     */
    public String type;

    // For "Prompt":
    public String provider;         // e.g., OpenAI, ChatGPT, etc.
    public String model;
    public String systemPrompt;
    public String userPrompt;

    // For "Text Replacement":
    public String textToReplace;
    public String replacementText;

    // For "TextToSpeech":
    // General parameters commonly supported by speech APIs.

    // Specifies which TTS provider to use, e.g., "ElevenLabs", "AmazonPolly", "GoogleTTS".
    public String ttsProvider;

    // The TTS model or engine to be used for speech synthesis.
    public String ttsModel;

    // The text to convert to speech. It can be plain text or SSML (optionally with an extra flag if needed).
    public String ttsText;

    // Language/Locale setting (e.g., "en-US", "de-DE").
    public String languageCode;

    // Voice selection: can be a name, ID, or even gender (male/female) depending on the provider.
    public String voiceId;

    // Optional: Style/model-specific parameters.
    // For Eleven Labs, parameters such as "stability" or "similarityBoost" might be provided.
    public Float stability;           // e.g., value between 0.0 and 1.0.
    public Float similarityBoost;     // e.g., value to enhance similarity to the original voice.



    // Audio output options.
    public String outputFormat;       // e.g., "mp3", "ogg", "wav".
    public Float speakingRate;        // e.g., 1.0 = normal speed.
    public Float pitch;               // e.g., 0.0 = default pitch, positive/negative values for higher/lower pitch.
    public Float volume;              // e.g., 1.0 = standard volume.

    // Additional parameters can be added if needed, such as SSML-specific settings or
    // extra configuration options for individual providers.
}