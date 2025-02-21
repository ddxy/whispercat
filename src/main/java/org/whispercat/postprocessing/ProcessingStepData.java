package org.whispercat.postprocessing;

/**
 * Data model class for a single processing step.
 */
public class ProcessingStepData {
    public String type;             // "Prompt" or "Text Replacement"
    // For Prompt:
    public String provider;
    public String model;
    public String systemPrompt;
    public String userPrompt;

    // For Text Replacement:
    public String textToReplace;
    public String replacementText;

    // TODO: Text to Speech
}
