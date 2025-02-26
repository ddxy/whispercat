package org.whispercat.postprocessing;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import org.whispercat.ConfigManager;
import org.whispercat.postprocessing.clients.OpenWebUIProcessClient;
import org.whispercat.recording.OpenAIClient;
import org.whispercat.recording.clients.ElevenLabsRecordingClient;
import org.whispercat.recording.clients.OpenAIRecordingClient;

import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class PostProcessingService {
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(PostProcessingService.class);
    private OpenAIClient openAIClient;
    private OpenWebUIProcessClient openWebUIClient;
    private ConfigManager configManager;

    public PostProcessingService(ConfigManager configManager) {
        this.configManager = configManager;
        this.openAIClient = new OpenAIClient(configManager);
        this.openWebUIClient = new OpenWebUIProcessClient(configManager);
    }

    public SwingWorker<String, Void> applyPostProcessing(String originalText, PostProcessingData postProcessingData) {
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                String processedText = originalText;
                for (ProcessingStepData step : postProcessingData.steps) {
                    if ("Prompt".equalsIgnoreCase(step.type)) {
                        processedText = performPromptProcessing(processedText, step);
                    } else if ("Text Replacement".equalsIgnoreCase(step.type)) {
                        processedText = processedText.replace(step.textToReplace, step.replacementText);
                    } else if ("Synthesizer".equalsIgnoreCase(step.type)) {
                        processedText = processSynthesizerStep(processedText, step);
                    } else {
                        logger.info("Unknown post-processing step type: " + step.type);
                    }
                }
                return processedText;
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    logger.info("Post processing complete. Result: " + result);
                    // Callback or further processing can go here.
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("Error during asynchronous post processing: ", ex);
                }
            }
        };
        worker.execute();
        return worker;
    }

    private String performPromptProcessing(String inputText, ProcessingStepData step) {
        logger.info("Pre-processing input: " + step.userPrompt);
        logger.info("Transcript: " + inputText);
        String fullUserPrompt = step.userPrompt.replaceAll("\\{\\{input}}", inputText);
        logger.info("Post-processing input: " + fullUserPrompt);
        try {
            if (step.provider.equalsIgnoreCase("OpenAI")) {
                logger.info("Processing using OpenAI API.");
                return openAIClient.processText(step.systemPrompt, fullUserPrompt, step.model);
            } else if (step.provider.equalsIgnoreCase("Open WebUI")) {
                logger.info("Processing using Open WebUI.");
                return openWebUIClient.processText(step.systemPrompt, fullUserPrompt, step.model);
            }
        } catch (IOException e) {
            logger.error("Error during prompt processing: ", e);
        }
        return inputText;
    }

    private String processSynthesizerStep(String processedText, ProcessingStepData step) throws Exception {

        if (step.ttsProvider.equalsIgnoreCase("ElevenLabs")) {
            int startIdx = step.voiceId.indexOf("(");
            int endIdx = step.voiceId.indexOf(")");
            if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
                String voiceId = step.voiceId.substring(startIdx + 1, endIdx);
                String outputFormat = "mp3_44100_128";
                ElevenLabsRecordingClient elevenLabsRecordingClient = new ElevenLabsRecordingClient(configManager, voiceId, outputFormat, step.ttsModel);
                File audioFile = elevenLabsRecordingClient.synthesize(processedText);
                if (audioFile != null) {
                    playAudioFile(audioFile);
                }
            } else {
                logger.error("Invalid ttsModel format: " + step.ttsModel);
            }
        } else if (step.ttsProvider.equalsIgnoreCase("OpenAI")) {
            File audioFile = new OpenAIRecordingClient(configManager).synthesize(processedText, step.ttsModel, step.voiceId);
            if (audioFile != null) {
                playAudioFile(audioFile);
            }
        } else {
            logger.error("Unknown synthesizer provider: " + step.provider);
        }


        return processedText;
    }

    private void playAudioFile(File audioFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(audioFile);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            Player player = new Player(bis);
            player.play();
        } catch (IOException | JavaLayerException ex) {
            logger.error("Error playing audio file: ", ex);
        }
    }
}