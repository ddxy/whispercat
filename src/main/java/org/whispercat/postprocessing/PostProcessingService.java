package org.whispercat.postprocessing;

import org.whispercat.ConfigManager;
import org.whispercat.recording.OpenAIClient;

import java.io.IOException;

public class PostProcessingService {

    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(PostProcessingService.class);

    // OpenAIClient instance used to make synchronous calls to the API.
    private OpenAIClient openAIClient;

    /**
     * Constructs the PostProcessingService with the given ConfigManager.
     * The OpenAIClient is initialized here.
     *
     * @param configManager The ConfigManager that contains configuration settings.
     */
    public PostProcessingService(ConfigManager configManager) {
        this.openAIClient = new OpenAIClient(configManager);
    }

    /**
     * Applies the defined post-processing steps sequentially.
     *
     * @param originalText       The initial transcribed text.
     * @param postProcessingData The configuration for post-processing.
     * @return The processed text after all steps.
     */
    public String applyPostProcessing(String originalText, PostProcessingData postProcessingData) {
        String processedText = originalText;

        // Iterate over all defined steps.
        for (ProcessingStepData step : postProcessingData.steps) {
            if ("Prompt".equalsIgnoreCase(step.type)) {
                // Use OpenAIClient for a synchronous call.
                processedText = performPromptProcessing(processedText, step);
            } else if ("Text Replacement".equalsIgnoreCase(step.type)) {
                // Replace text based on configuration.
                processedText = processedText.replace(step.textToReplace, step.replacementText);
            } else {
                // Log unknown step type.
                System.out.println("Unknown post-processing step type: " + step.type);
            }
        }

        return processedText;
    }

    /**
     * Synchronously processes the text using OpenAI API via a prompt.
     * The processing is executed step-by-step. After receiving the API response,
     * the result is used as input for the next processing step.
     *
     * @param inputText The input text to process.
     * @param step      The processing configuration.
     * @return The processed text from the OpenAI response.
     */
    private String performPromptProcessing(String inputText, ProcessingStepData step) {

        logger.info("Pre-processing input: " + step.userPrompt);
        logger.info("Transcript: " + inputText);
        // Combine the user prompt with the input text.
        String fullUserPrompt = step.userPrompt.replaceAll("\\{\\{input}}", inputText);
        logger.info("Post-processing input: " + fullUserPrompt);
        try {
            // Synchronous call using the provided OpenAIClient.
            String result = openAIClient.processText(step.systemPrompt, fullUserPrompt, step.model);
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return inputText;
    }
}