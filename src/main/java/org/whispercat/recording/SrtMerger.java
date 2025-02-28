package org.whispercat.recording;

import java.io.*;
import java.util.*;
import java.util.regex.*;

// This class contains methods to parse, merge, and write SRT files.
// All previous functionalities are preserved except for the parseSrt method
// which has been adapted to accept a String parameter for parsing the SRT content.
public class SrtMerger {

    // Class to represent a single subtitle block
    public static class SubtitleBlock {
        public int sequence;
        public long startMillis;
        public long endMillis;
        public String text;

        public SubtitleBlock(int sequence, long startMillis, long endMillis, String text) {
            this.sequence = sequence;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.text = text;
        }
    }

    // Convert an SRT time string "hh:mm:ss,ms" to milliseconds
    public static long parseTimeToMillis(String timeStr) {
        String[] parts = timeStr.split("[:,]");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);
        int millis = Integer.parseInt(parts[3]);
        return hours * 3600000L + minutes * 60000L + seconds * 1000L + millis;
    }

    // Convert milliseconds to an SRT time string "hh:mm:ss,ms"
    public static String millisToTime(long millis) {
        long hours = millis / 3600000;
        millis %= 3600000;
        long minutes = millis / 60000;
        millis %= 60000;
        long seconds = millis / 1000;
        millis %= 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    /**
     * Parses SRT content provided as a String and returns a list of subtitle blocks.
     *
     * Modifications:
     * - The method now accepts a String parameter containing the SRT content instead of a File object.
     *   This change ensures that the input SRT content is provided as a String.
     *
     * @param srtContent the SRT content as a String
     * @return a list of SubtitleBlock objects parsed from the SRT content
     * @throws IOException if an I/O error occurs during reading
     */
    public static List<SubtitleBlock> parseSrt(String srtContent) throws IOException {
        if(srtContent == null || srtContent.isEmpty()) {
            return Collections.emptyList();
        }
        List<SubtitleBlock> blocks = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new StringReader(srtContent))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                // Parse the sequence number of the block
                int sequence = Integer.parseInt(line.trim());
                // Parse the time range line
                String timeLine = br.readLine();
                if (timeLine == null) {
                    break;
                }
                String[] timeParts = timeLine.split("-->");
                String startTimeStr = timeParts[0].trim();
                String endTimeStr = timeParts[1].trim();
                long startMillis = parseTimeToMillis(startTimeStr);
                long endMillis = parseTimeToMillis(endTimeStr);

                // Read subtitle text until an empty line is encountered
                StringBuilder textBuilder = new StringBuilder();
                while ((line = br.readLine()) != null && !line.trim().isEmpty()) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n");
                    }
                    textBuilder.append(line);
                }
                blocks.add(new SubtitleBlock(sequence, startMillis, endMillis, textBuilder.toString()));
            }
        }
        return blocks;
    }

    // Existing method to parse an SRT file from a File object (kept for backward compatibility)
    public static List<SubtitleBlock> parseSrt(File file) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
        }
        // Reuse the String-based parseSrt method to avoid code duplication
        return parseSrt(contentBuilder.toString());
    }

    // Merge two lists of subtitle blocks by shifting the timings of the second list so that
    // its timings follow after the first list. A gap (in milliseconds) can be added between the two parts.
    public static List<SubtitleBlock> mergeSrtBlocks(List<SubtitleBlock> list1, List<SubtitleBlock> list2, long gapMillis) {
        long offset = 0;
        if (!list1.isEmpty()) {
            // Set offset to the end time of the last subtitle block in list1 plus the desired gap
            offset = list1.get(list1.size() - 1).endMillis + gapMillis;
        }
        List<SubtitleBlock> merged = new ArrayList<>();
        merged.addAll(list1);
        // Adjust the timings for each block in list2 and add them to the merged list
        for (SubtitleBlock block : list2) {
            merged.add(new SubtitleBlock(0, block.startMillis + offset, block.endMillis + offset, block.text));
        }
        // Renumber all subtitle blocks sequentially
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).sequence = i + 1;
        }
        return merged;
    }

    // Write a list of subtitle blocks in SRT format to the specified output file
    public static void writeSrt(List<SubtitleBlock> blocks, File outputFile) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"))) {
            for (SubtitleBlock block : blocks) {
                bw.write(String.valueOf(block.sequence));
                bw.newLine();
                String timeLine = millisToTime(block.startMillis) + " --> " + millisToTime(block.endMillis);
                bw.write(timeLine);
                bw.newLine();
                bw.write(block.text);
                bw.newLine();
                bw.newLine();
            }
        }
    }

    // Main method for demonstrating the SRT merging functionality
    public static void main(String[] args) {
        try {
            // Read SRT content from files (using the file-based parseSrt method)
            File srtFile1 = new File("subtitle1.srt");
            File srtFile2 = new File("subtitle2.srt");

            List<SubtitleBlock> list1 = parseSrt(srtFile1);
            List<SubtitleBlock> list2 = parseSrt(srtFile2);

            // Alternatively, if you have SRT content as Strings, you can use the String-based parseSrt method:
            /*
            String srtContent1 = "1\n00:00:01,000 --> 00:00:04,000\nHello, welcome to our presentation.\n\n2\n00:00:05,000 --> 00:00:08,000\nToday, we are going to discuss our recent projects.\n";
            String srtContent2 = "1\n00:00:01,000 --> 00:00:04,000\nThis is the second file.\n";
            List<SubtitleBlock> list1 = parseSrt(srtContent1);
            List<SubtitleBlock> list2 = parseSrt(srtContent2);
            */

            // Define a gap between the two subtitle files in milliseconds (e.g., 1000ms = 1 second)
            long gapMillis = 1000;

            // Merge SRT blocks with proper time shifting for the second file
            List<SubtitleBlock> mergedBlocks = mergeSrtBlocks(list1, list2, gapMillis);

            // Write the merged subtitles to the output file
            File mergedOutput = new File("merged.srt");
            writeSrt(mergedBlocks, mergedOutput);

            System.out.println("SRT files merged successfully into " + mergedOutput.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}