package org.whispercat.recording;

import java.io.*;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * This class contains methods to parse, merge, and write JSON transcript files
 * with word-level timestamps using Gson for JSON processing.
 * All previous functionalities are preserved except for the parse method,
 * which has been adapted to accept a JSON string parameter for parsing
 * the transcript content.
 */
public class WordTimestampMerger {

    // Class to represent a word with its timestamp information in internal representation (milliseconds)
    public static class Word {
        public String word;
        public long startMillis;
        public long endMillis;

        public Word(String word, long startMillis, long endMillis) {
            this.word = word;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }

    // Internal class to match the JSON structure for each word (using seconds as double)
    private static class WordJson {
        String word;
        double start;
        double end;
    }

    // Internal class to match the entire JSON transcript structure
    private static class Transcript {
        List<WordJson> words;
    }

    // Extended transcript class that also includes the full text field.
    private static class TranscriptExtended {
        String text;
        List<WordJson> words;
    }

    // Convert seconds (as double) to milliseconds
    public static long secondsToMillis(double seconds) {
        return (long) (seconds * 1000);
    }

    // Convert milliseconds to seconds as double (rounded to three decimals)
    public static double millisToSeconds(long millis) {
        return Math.round(millis / 1000.0 * 1000.0) / 1000.0;
    }

    /**
     * Parses JSON transcript content provided as a String and returns a list of words.
     *
     * Modifications:
     * - The method now accepts a String parameter containing the JSON content instead of a File.
     *
     * @param jsonContent the JSON transcript content as a String.
     * @return a list of Word objects parsed from the JSON content.
     * @throws IOException if an I/O error occurs during reading or if the JSON format is invalid.
     */
    public static List<Word> parseJson(String jsonContent) throws IOException {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Word> wordsList = new ArrayList<>();
        try {
            Gson gson = new Gson();
            Transcript transcript = gson.fromJson(jsonContent, Transcript.class);
            if (transcript == null || transcript.words == null) {
                throw new IOException("JSON does not contain a 'words' array");
            }
            for (WordJson wj : transcript.words) {
                long startMillis = secondsToMillis(wj.start);
                long endMillis = secondsToMillis(wj.end);
                wordsList.add(new Word(wj.word, startMillis, endMillis));
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Error parsing JSON content", e);
        }
        return wordsList;
    }

    /**
     * Existing method to parse a JSON transcript from a File object (kept for backward compatibility).
     *
     * @param file the file containing JSON transcript content.
     * @return a list of Word objects parsed from the file.
     * @throws IOException if an I/O error occurs during reading or if the JSON format is invalid.
     */
    public static List<Word> parseJson(File file) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
        }
        // Reuse the String-based parseJson method to avoid code duplication.
        return parseJson(contentBuilder.toString());
    }

    /**
     * New method that parses JSON transcript content provided as a String and returns a list of words.
     * In addition to the functionality of parseJson(String jsonContent),
     * this method also preserves punctuation and the original case if the JSON contains a "text" field.
     * If the number of tokens from the "text" (split by whitespace) matches the number of words,
     * the tokens are used instead of the values from the "words" array.
     *
     * @param jsonContent the JSON transcript content as a String.
     * @return a list of Word objects parsed from the JSON content with punctuation and case preserved.
     * @throws IOException if an I/O error occurs during reading or if the JSON format is invalid.
     */
    public static List<Word> parseJsonPreserve(String jsonContent) throws IOException {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Word> wordsList = new ArrayList<>();
        try {
            Gson gson = new Gson();
            // First try to parse as an extended transcript including the "text" field.
            TranscriptExtended transcriptExt = gson.fromJson(jsonContent, TranscriptExtended.class);
            if (transcriptExt == null || transcriptExt.words == null) {
                throw new IOException("JSON does not contain a 'words' array");
            }
            // If the extended transcript has text, try to preserve punctuation and case.
            if (transcriptExt.text != null && !transcriptExt.text.trim().isEmpty()) {
                // Split the text by whitespace to capture tokens that include punctuation.
                String[] tokens = transcriptExt.text.trim().split("\\s+");
                if (tokens.length == transcriptExt.words.size()) {
                    // Use the tokens from the full text for preserving punctuation and case.
                    for (int i = 0; i < tokens.length; i++) {
                        WordJson wj = transcriptExt.words.get(i);
                        long startMillis = secondsToMillis(wj.start);
                        long endMillis = secondsToMillis(wj.end);
                        wordsList.add(new Word(tokens[i], startMillis, endMillis));
                    }
                    return wordsList;
                }
                // If token count does not match, fall back to using the original word values.
            }
            // Fallback behavior: use the word values from the words array.
            Transcript transcript = gson.fromJson(jsonContent, Transcript.class);
            if (transcript == null || transcript.words == null) {
                throw new IOException("JSON does not contain a 'words' array");
            }
            for (WordJson wj : transcript.words) {
                long startMillis = secondsToMillis(wj.start);
                long endMillis = secondsToMillis(wj.end);
                wordsList.add(new Word(wj.word, startMillis, endMillis));
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Error parsing JSON content", e);
        }
        return wordsList;
    }

    /**
     * Merges two lists of words by combining them and then sorting the resulting list
     * by the start time of each word.
     *
     * Modifications:
     * - Instead of shifting the second list with a time offset, the lists are simply combined
     *   and sorted by the word's start timestamp.
     *
     * @param list1 the first list of Word objects.
     * @param list2 the second list of Word objects.
     * @return a merged and sorted list of Word objects based on start time.
     */
    public static List<Word> mergeWords(List<Word> list1, List<Word> list2) {
        List<Word> merged = new ArrayList<>();
        merged.addAll(list1);
        merged.addAll(list2);
        // Sort merged list by startMillis in ascending order.
        merged.sort(Comparator.comparingLong(word -> word.startMillis));
        return merged;
    }

    /**
     * New method that adds a given offset in milliseconds to each word's start and end timestamps.
     * This is useful when processing transcript parts that were transcribed separately
     * to recreate a global timeline.
     *
     * For example, if Part 1 is 3:00 minutes long, then for Part 2 the offset will be 3 minutes (in ms).
     *
     * @param words       the list of Word objects whose times are to be adjusted.
     * @param offsetMillis the offset in milliseconds to add.
     * @return a new list of Word objects with adjusted start and end times.
     */
    public static List<Word> addOffsetToWords(List<Word> words, long offsetMillis) {
        List<Word> adjusted = new ArrayList<>();
        for (Word w : words) {
            adjusted.add(new Word(w.word, w.startMillis + offsetMillis, w.endMillis + offsetMillis));
        }
        return adjusted;
    }

    /**
     * Writes a list of words in JSON format to the specified output file.
     * The JSON structure includes a "words" array where each word object contains:
     * "word", "start" (in seconds), and "end" (in seconds).
     *
     * @param words      the list of Word objects to be serialized as JSON.
     * @param outputFile the file to which the JSON output will be written.
     * @throws IOException if an I/O error occurs during writing.
     */
    public static void writeJson(List<Word> words, File outputFile) throws IOException {
        // Create a structure matching the original JSON format
        Map<String, Object> jsonMap = new HashMap<>();
        List<Map<String, Object>> wordsArray = new ArrayList<>();
        for (Word w : words) {
            Map<String, Object> wordEntry = new HashMap<>();
            wordEntry.put("word", w.word);
            // Convert milliseconds back to seconds as in the original JSON format
            wordEntry.put("start", millisToSeconds(w.startMillis));
            wordEntry.put("end", millisToSeconds(w.endMillis));
            wordsArray.add(wordEntry);
        }
        jsonMap.put("words", wordsArray);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(jsonMap);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"))) {
            bw.write(jsonOutput);
        }
    }

    /**
     * Main method for demonstrating the JSON transcript merging and offset adjustment functionality.
     *
     * This method demonstrates:
     * - Parsing JSON content from files.
     * - Alternatively, parsing JSON content from Strings (commented example).
     * - Merging the transcript word lists.
     * - Adjusting the transcript times using an offset.
     * - Writing the merged transcript to an output file.
     */
    public static void main(String[] args) {
        try {
            // Read JSON transcript content from files (using the file-based parseJson method)
            File jsonFile1 = new File("transcript1.json");
            File jsonFile2 = new File("transcript2.json");
            List<Word> list1 = parseJson(jsonFile1);
            List<Word> list2 = parseJson(jsonFile2);

            // For demonstration purposes, assume list2 corresponds to a later part of the audio.
            // For example, if the first part is 3:00 minutes long (i.e. 180,000 ms),
            // then add an offset of 180,000 ms to all words from list2.
            long offsetForList2 = 180_000;
            List<Word> adjustedList2 = addOffsetToWords(list2, offsetForList2);

            // Merge transcript words by simply combining and sorting by start time.
            List<Word> mergedWords = mergeWords(list1, adjustedList2);

            // Write the merged transcript words to the output file in JSON format.
            File mergedOutput = new File("merged_transcript.json");
            writeJson(mergedWords, mergedOutput);
            System.out.println("Transcript files merged successfully into " + mergedOutput.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}