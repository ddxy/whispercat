package org.whispercat.postprocessing;

import java.util.List;

/**
 * Data model class representing the overall post-processing settings.
 */
public class PostProcessingData {
    public String uuid; // Unique identifier.
    public String title;
    public String description; // New description field.
    public  List<ProcessingStepData> steps;
}
