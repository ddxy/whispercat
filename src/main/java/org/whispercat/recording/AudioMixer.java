package org.whispercat.recording;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges or splits WAV files (e.g., speaker and microphone files) and performs audio operations.
 *
 * All previous functionalities remain unchanged.
 */
public class AudioMixer {

    /**
     * Merges two WAV files (e.g., a speaker file and a microphone file) into a single WAV file.
     *
     * Special cases:
     * - If file1 is null but file2 is not null, file2 is copied to outputFile.
     * - If file2 is null but file1 is not null, file1 is copied to outputFile.
     * - If both files are null, an IllegalArgumentException is thrown.
     * - If one of the files is "empty" (i.e., contains no audio frames), the non-empty file is copied.
     *
     * If both files contain data, the method converts them to a common target format and merges them,
     * even if the audio lengths differ. When one file ends before the other, the missing data is
     * treated as silence.
     *
     * The target format in this example is PCM_SIGNED, 44100 Hz, 16-bit, mono, little endian.
     *
     * @param file1      The first input WAV file (can be null).
     * @param file2      The second input WAV file (can be null).
     * @param outputFile The output file where the merged audio is written.
     * @throws Exception If an error occurs during reading or writing.
     */
    public static void mergeAudioFiles(File file1, File file2, File outputFile) throws Exception {
        // --- Null-Handling ---
        if (file1 == null && file2 == null) {
            throw new IllegalArgumentException("Both input files are null.");
        } else if (file1 == null && file2 != null) {
            Files.copy(file2.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("file1 is null - file2 was copied to outputFile.");
            return;
        } else if (file2 == null && file1 != null) {
            Files.copy(file1.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("file2 is null - file1 was copied to outputFile.");
            return;
        }

        // --- Open the original AudioInputStreams ---
        AudioInputStream originalStream1 = AudioSystem.getAudioInputStream(file1);
        AudioInputStream originalStream2 = AudioSystem.getAudioInputStream(file2);
        long frames1 = originalStream1.getFrameLength();
        long frames2 = originalStream2.getFrameLength();

        // --- Check for empty files ---
        if (frames1 == 0 && frames2 > 0) {
            originalStream1.close();
            originalStream2.close();
            Files.copy(file2.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("file1 is empty - file2 was copied to outputFile.");
            return;
        } else if (frames2 == 0 && frames1 > 0) {
            originalStream1.close();
            originalStream2.close();
            Files.copy(file1.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("file2 is empty - file1 was copied to outputFile.");
            return;
        } else if (frames1 == 0 && frames2 == 0) {
            originalStream1.close();
            originalStream2.close();
            Files.copy(file1.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Both files are empty - file1 was copied to outputFile.");
            return;
        }

        // --- Define the target format ---
        // PCM_SIGNED, 44100 Hz, 16-bit, mono, little endian
        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100, // Sample Rate
                16,    // Sample Size in Bits
                1,     // Channels (mono)
                2,     // Frame Size (2 bytes = 16-bit mono)
                44100, // Frame Rate
                false  // Little Endian
        );

        // --- Convert the streams to the target format ---
        AudioInputStream stream1 = AudioSystem.getAudioInputStream(targetFormat, originalStream1);
        AudioInputStream stream2 = AudioSystem.getAudioInputStream(targetFormat, originalStream2);
        // IMPORTANT: Do not close originalStream1 and originalStream2 here as they are still needed
        // by the converted streams. This prevents the "Stream closed" error.

        int frameSize = targetFormat.getFrameSize(); // should be 2 bytes
        // We read framewise, i.e., 2 bytes per iteration.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            byte[] frameBuffer1 = new byte[frameSize];
            byte[] frameBuffer2 = new byte[frameSize];
            int r1 = stream1.read(frameBuffer1);
            int r2 = stream2.read(frameBuffer2);
            // If both streams have reached the end, break.
            if (r1 == -1 && r2 == -1) {
                break;
            }
            // If one stream doesn't provide a frame, fill the frame with silence.
            if (r1 == -1) {
                Arrays.fill(frameBuffer1, (byte) 0);
            }
            if (r2 == -1) {
                Arrays.fill(frameBuffer2, (byte) 0);
            }
            // Read the 16-bit (2-byte) sample from both frames (Little Endian).
            short sample1 = (short) (((frameBuffer1[1] & 0xFF) << 8) | (frameBuffer1[0] & 0xFF));
            short sample2 = (short) (((frameBuffer2[1] & 0xFF) << 8) | (frameBuffer2[0] & 0xFF));
            // Mix the samples (here by averaging).
            int mixedSample = (sample1 + sample2) / 2;
            // Write the mixed sample (little endian) into the output buffer.
            baos.write((byte) (mixedSample & 0xFF));
            baos.write((byte) ((mixedSample >> 8) & 0xFF));
        }
        // Close the converted streams since they're no longer needed
        stream1.close();
        stream2.close();
        // Now it's safe to close the original streams if needed.
        originalStream1.close();
        originalStream2.close();

        byte[] mergedBytes = baos.toByteArray();
        // Create an AudioInputStream from the merged bytes.
        ByteArrayInputStream bais = new ByteArrayInputStream(mergedBytes);
        AudioInputStream mixedStream = new AudioInputStream(bais, targetFormat, mergedBytes.length / frameSize);
        // Write the result to outputFile in WAV format.
        AudioSystem.write(mixedStream, AudioFileFormat.Type.WAVE, outputFile);
        mixedStream.close();
        System.out.println("Merging completed: " + outputFile.getAbsolutePath());
    }

    /**
     * Splits an audio file (WAV format) into multiple parts so that each part does not exceed the specified maximum file size.
     *
     * The method computes the maximum duration (in frames) for each part based on maxSizeBytes and returns a list of Files.
     * This method is preserved for backward compatibility.
     *
     * @param inputFile     The original WAV file to be split.
     * @param maxSizeBytes  The maximum file size (in bytes) allowed for each split part.
     * @param outputDir     The directory where the split parts will be written.
     * @return A List of Files representing the split parts.
     * @throws Exception If an error occurs during splitting.
     */
    public static List<File> splitAudioFile(File inputFile, long maxSizeBytes, File outputDir) throws Exception {
        List<File> parts = new ArrayList<>();
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputFile);
        AudioFormat format = audioInputStream.getFormat();
        long totalFrames = audioInputStream.getFrameLength();
        float frameRate = format.getFrameRate();
        int frameSize = format.getFrameSize();
        double bytesPerSecond = frameRate * frameSize;
        // Assume a WAV header of approx. 44 bytes in each file.
        double maxDurationSec = (maxSizeBytes - 44) / bytesPerSecond;
        long maxFramesPerPart = (long) (maxDurationSec * frameRate);
        int partNumber = 1;

        // Make sure the output directory exists.
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        while (totalFrames > 0) {
            long framesForThisPart = Math.min(maxFramesPerPart, totalFrames);
            // Create an AudioInputStream for this part.
            AudioInputStream partStream = new AudioInputStream(audioInputStream, format, framesForThisPart);
            File outFile = new File(outputDir,
                    inputFile.getName().replaceFirst("(?i)\\.wav$", "") + "_part" + partNumber + ".wav");
            AudioSystem.write(partStream, AudioFileFormat.Type.WAVE, outFile);
            parts.add(outFile);
            System.out.println("Created split part: " + outFile.getAbsolutePath());
            partNumber++;
            totalFrames -= framesForThisPart;
        }
        audioInputStream.close();
        return parts;
    }

    /**
     * New helper class to hold the results of splitting:
     * a list of File parts and a list of offsets (in milliseconds) for each part.
     */
    public static class SplitResult {
        public List<File> parts;
        public List<Long> offsets;

        public SplitResult(List<File> parts, List<Long> offsets) {
            this.parts = parts;
            this.offsets = offsets;
        }
    }

    /**
     * Splits an audio file (either WAV or MP3) into multiple parts with cumulative offsets.
     * This method selects the appropriate splitting routine based on the file extension.
     *
     * @param inputFile    The original audio file to be split.
     * @param maxSizeBytes The maximum file size (in bytes) for each split part.
     * @param outputDir    The directory where the split parts will be written.
     * @return A SplitResult containing a list of File parts and their cumulative offsets (in ms).
     * @throws Exception If the file format is unsupported or an error occurs during splitting.
     */
    public static SplitResult splitFileWithOffsets(File inputFile, long maxSizeBytes, File outputDir) throws Exception {
        if (inputFile == null) {
            return null;
        }
        String fileName = inputFile.getName().toLowerCase();
        if (fileName.endsWith(".mp3")) {
            // Call the MP3 splitting method if the file extension is .mp3
            return splitMP3FileWithOffsets(inputFile, maxSizeBytes, outputDir);
        } else if (fileName.endsWith(".wav")) {
            // Call the WAV splitting method if the file extension is .wav
            return splitWavFileIgnoringHeaderWithOffsets(inputFile, maxSizeBytes, outputDir);
        } else {
            throw new IllegalArgumentException("Unsupported file type. Only MP3 and WAV are supported.");
        }
    }

    public static SplitResult splitWavFileIgnoringHeaderWithOffsets(File inputFile, long maxSizeBytes, File outputDir) throws Exception {
        if (inputFile == null) {
            return null;
        }

        if (inputFile.length() <= maxSizeBytes) {
            // If the input file's size is less than or equal to maxSizeBytes, no splitting is needed.
            List<File> parts = new ArrayList<>();
            List<Long> offsets = new ArrayList<>();
            // Add the original file as the only part.
            parts.add(inputFile);
            offsets.add(0L);
            return new SplitResult(parts, offsets);
        }

        List<File> parts = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();

        // Lese die gesamte Datei als Rohdaten ein
        byte[] rawData = Files.readAllBytes(inputFile.toPath());
        if (rawData.length <= 44) {
            // Datei zu klein, um einen Header und Audiodaten zu enthalten
            return new SplitResult(parts, offsets);
        }
        // Überspringe die ersten 44 Bytes (angenommener WAV-Header)
        byte[] pcmData = Arrays.copyOfRange(rawData, 44, rawData.length);

        // Definiere ein manuelles AudioFormat basierend auf den bekannten Parametern
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                20000f,    // Sample Rate
                16,        // Sample Size in Bits
                1,         // Channels
                2,         // Frame Size (1 Channel * 2 Bytes)
                20000f,    // Frame Rate
                false      // Little Endian
        );
        int frameSize = format.getFrameSize();
        float frameRate = format.getFrameRate();
        double bytesPerSecond = frameRate * frameSize;

        // Berechne die maximale Dauer pro Part (in Sekunden) – hier verwenden wir maxSizeBytes direkt, Header ist schon entfernt
        double maxDurationSec = maxSizeBytes / bytesPerSecond;
        long maxFramesPerPart = (long)(maxDurationSec * frameRate);

        int partNumber = 1;
        long currentOffset = 0; // in Millisekunden

        int totalFrames = pcmData.length / frameSize;
        int byteIndex = 0;

        // Sicherstellen, dass das Ausgabeverzeichnis existiert.
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Teile den Datenpuffer in Chunks auf
        while (byteIndex < pcmData.length) {
            long framesForThisPart = Math.min(maxFramesPerPart, totalFrames);
            int bytesForThisPart = (int)(framesForThisPart * frameSize);

            // Stelle sicher, dass bytesForThisPart ein ganzzahliges Vielfaches von frameSize ist.
            bytesForThisPart = bytesForThisPart - (bytesForThisPart % frameSize);

            // Extrahiere den Chunk
            byte[] chunkData = Arrays.copyOfRange(pcmData, byteIndex, byteIndex + bytesForThisPart);
            ByteArrayInputStream bais = new ByteArrayInputStream(chunkData);
            AudioInputStream partStream = new AudioInputStream(bais, format, framesForThisPart);

            // Erzeuge den Ausgabedateinamen
            String nameWithoutExt = inputFile.getName().replaceFirst("(?i)\\.wav$", "");
            File outFile = new File(outputDir, nameWithoutExt + "_part" + partNumber + ".wav");

            // Schreibe den Chunk als WAV-Datei
            AudioSystem.write(partStream, AudioFileFormat.Type.WAVE, outFile);
            parts.add(outFile);
            offsets.add(currentOffset);
            System.out.println("Created split part: " + outFile.getAbsolutePath() + " with " + framesForThisPart + " frames.");

            long partDurationMs = (long)((framesForThisPart / frameRate) * 1000);
            currentOffset += partDurationMs;
            partNumber++;

            byteIndex += bytesForThisPart;
            totalFrames -= framesForThisPart;
        }

        return new SplitResult(parts, offsets);
    }

    /**
     * New method that splits an audio file (WAV format) into multiple parts so that each part does not exceed the specified
     * maximum file size. Additionally, it calculates and returns the cumulative offsets (in milliseconds) for each part.
     * For example, for a split into multiple parts:
     * - The first part always has offset 0.
     * - The second part's offset equals the duration of the first part.
     * - The third part's offset equals the duration of the first plus second parts, and so on.
     *
     * @param inputFile    The original WAV file to be split.
     * @param maxSizeBytes The maximum file size (in bytes) allowed for each split part.
     * @param outputDir    The directory where the split parts will be written.
     * @return A SplitResult containing a list of File parts and a list of offset values in milliseconds.
     * @throws Exception If an error occurs during splitting.
     */
    public static SplitResult splitWavAudioFileWithOffsets1(File inputFile, long maxSizeBytes, File outputDir) throws Exception {
        if(inputFile == null) {
            return null;
        }
        List<File> parts = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();

        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputFile);
        AudioFormat format = audioInputStream.getFormat();
        long fileSize = inputFile.length();
        int frameSize = format.getFrameSize();
        long totalFrames = (fileSize - 44) / frameSize;
        float frameRate = format.getFrameRate();
        double bytesPerSecond = frameRate * frameSize;
        // Assume a WAV header of approx. 44 bytes in each file.
        double maxDurationSec = (maxSizeBytes - 44) / bytesPerSecond;
        long maxFramesPerPart = (long) (maxDurationSec * frameRate);
        int partNumber = 1;
        long currentOffset = 0; // in milliseconds

        // Ensure the output directory exists.
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // While there are frames left in the audio stream.
        while (totalFrames > 0) {
            long framesForThisPart = Math.min(maxFramesPerPart, totalFrames);
            // Add the current offset for this part.
            offsets.add(currentOffset);
            // Create an AudioInputStream for this part.
            AudioInputStream partStream = new AudioInputStream(audioInputStream, format, framesForThisPart);
            File outFile = new File(outputDir,
                    inputFile.getName().replaceFirst("(?i)\\.wav$", "") + "_part" + partNumber + ".wav");
            AudioSystem.write(partStream, AudioFileFormat.Type.WAVE, outFile);
            parts.add(outFile);
            System.out.println("Created split part: " + outFile.getAbsolutePath());
            partNumber++;

            // Calculate the duration (in milliseconds) of this part.
            long partDurationMs = (long) ((framesForThisPart / frameRate) * 1000);
            currentOffset += partDurationMs;
            totalFrames -= framesForThisPart;
        }
        audioInputStream.close();
        return new SplitResult(parts, offsets);
    }

    /**
     * Splits an MP3 file into multiple MP3 parts with cumulative offsets.
     * The method reads the input MP3 file in chunks (each of approximately maxSizeBytes bytes),
     * and writes each chunk as a separate MP3 file. It then calculates and returns the cumulative offsets (in milliseconds)
     * for each part.
     *
     * Note: This method assumes that an MP3 SPI is installed (e.g., mp3spi, jl, and tritonus_share) so that
     * AudioSystem.write() accepts an MP3 output type.
     *
     * @param inputFile    The original MP3 file to be split.
     * @param maxSizeBytes The maximum file size (in bytes) for each split part.
     * @param outputDir    The directory where the split MP3 parts will be written.
     * @return A SplitResult containing a list of MP3 File parts and a list of cumulative offsets (in ms) for each part.
     * @throws Exception If an error occurs during splitting.
     */
    public static SplitResult splitMP3FileWithOffsets(File inputFile, long maxSizeBytes, File outputDir) throws Exception {
        if (inputFile == null) {
            return null;
        }
        List<File> parts = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();

        // Open the MP3 file. This assumes your MP3 SPI is installed.
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputFile);
        AudioFormat originalFormat = audioInputStream.getFormat();
        int frameSize = originalFormat.getFrameSize();
        // For compressed formats such as MP3, the frameSize might not be defined.
        // In that case, we convert the stream to PCM_SIGNED.
        if (frameSize <= 0) {
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    originalFormat.getSampleRate(),
                    16,
                    originalFormat.getChannels(),
                    originalFormat.getChannels() * 2,
                    originalFormat.getSampleRate(),
                    false);
            audioInputStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream);
            originalFormat = audioInputStream.getFormat();
            frameSize = originalFormat.getFrameSize();
        }

        float frameRate = originalFormat.getFrameRate();
        double bytesPerSecond = frameRate * frameSize;
        // Determine the maximum duration in seconds that corresponds to maxSizeBytes.
        double maxDurationSec = (maxSizeBytes - 44) / bytesPerSecond;
        long maxFramesPerPart = (long)(maxDurationSec * frameRate);

        // Instead of using totalFrames (which can be invalid for compressed files),
        // we accumulate audio data in chunks until the end of stream is reached.
        int bufferSize = 4096;
        byte[] buffer = new byte[bufferSize];
        int bytesRead = 0;
        int partNumber = 1;
        long currentOffset = 0; // in milliseconds

        // Ensure the output directory exists.
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        while (true) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            long framesAccumulated = 0;

            // Read data until maximum frames per part are reached or EOF.
            while (framesAccumulated < maxFramesPerPart) {
                bytesRead = audioInputStream.read(buffer);
                if (bytesRead == -1) break;
                // Ensure we only consider whole frames.
                int validBytes = bytesRead - (bytesRead % frameSize);
                baos.write(buffer, 0, validBytes);
                framesAccumulated += validBytes / frameSize;
            }

            if (framesAccumulated == 0) break;

            offsets.add(currentOffset);
            byte[] chunkData = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(chunkData);
            AudioInputStream partStream = new AudioInputStream(bais, originalFormat, framesAccumulated);

            // Create an AudioFileFormat.Type for MP3.
            // This requires that an MP3 implementation is available.
            AudioFileFormat.Type mp3Type = new AudioFileFormat.Type("MP3", "mp3");
            String nameWithoutExt = inputFile.getName().replaceFirst("(?i)\\.\\w+$", "");
            File outFile = new File(outputDir, nameWithoutExt + "_part" + partNumber + ".mp3");

            // Write the part as an MP3 file.
            AudioSystem.getAudioFileTypes();
            AudioSystem.write(partStream, mp3Type, outFile);
            parts.add(outFile);
            System.out.println("Created MP3 split part: " + outFile.getAbsolutePath() + " with " + framesAccumulated + " frames.");

            long partDurationMs = (long)((framesAccumulated / frameRate) * 1000);
            currentOffset += partDurationMs;
            partNumber++;
            if (bytesRead == -1) break;
        }
        audioInputStream.close();
        return new SplitResult(parts, offsets);
    }

    /**
     * Converts an MP3 file to a WAV file by decoding it to PCM_SIGNED format.
     *
     * @param mp3File   The input MP3 file.
     * @param outputDir The directory where the resulting WAV file will be stored.
     * @return The converted WAV File.
     * @throws Exception If an error occurs during conversion.
     */
    public static File convertMp3ToWav(File mp3File, File outputDir) throws Exception {
        if (mp3File == null) {
            throw new IllegalArgumentException("Input file is null");
        }
        // Ensure the output directory exists.
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        // Derive a WAV filename from the MP3 filename.
        String baseName = mp3File.getName().replaceFirst("(?i)\\.mp3$", "");
        File wavFile = new File(outputDir, baseName + "_" + System.currentTimeMillis() + ".wav");

        // Obtain an AudioInputStream for the MP3 file.
        AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(mp3File);
        AudioFormat baseFormat = mp3Stream.getFormat();

        // Convert the stream to PCM_SIGNED format.
        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2, // 2 bytes per sample
                baseFormat.getSampleRate(),
                false);

        AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, mp3Stream);

        // Write the PCM stream to a WAV file.
        AudioSystem.write(pcmStream, AudioFileFormat.Type.WAVE, wavFile);

        pcmStream.close();
        mp3Stream.close();

        System.out.println("Converted MP3 to WAV: " + wavFile.getAbsolutePath());
        return wavFile;
    }

    // Example main method to test the merge and split functionalities.
    public static void main(String[] args) {
        try {
            // Merge example
            File audioFile = new File("speaker.wav"); // e.g., audio file from speakers (or set to null)
            File micFile = new File("mic.wav");         // e.g., audio file from microphone (or set to null)
            File mergedFile = new File("merged.wav");
            mergeAudioFiles(audioFile, micFile, mergedFile);

            // Split example using the new splitAudioFileWithOffsets method:
            // Define maximum file size (in bytes). 2MB = 2 * 1024 * 1024.
            long maxSizeBytes = 24 * 1024 * 1024;  // Adjust if needed.
            File inputAudioFile = mergedFile;  // or any other WAV file.
            File outputDirectory = new File("split_parts");
            SplitResult result = splitWavFileIgnoringHeaderWithOffsets(inputAudioFile, maxSizeBytes, outputDirectory);
            System.out.println("Audio file split into " + result.parts.size() + " parts.");
            System.out.println("Offsets (ms) for each part: " + result.offsets);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}