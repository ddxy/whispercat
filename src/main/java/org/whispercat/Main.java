package org.whispercat;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) {
        // API-Key einfügen
        String apiKey = "";    // Deinen API-Key hier einfügen

        // Die Voice-ID aus deinem Beispiel
        String voiceId = "RT0Ws4wraMnx4S5vInNL";

        // Output-Format als Query-Parameter (mp3_44100_128)
        String outputFormat = "mp3_44100_128";

        // Der Text, der in Sprache umgewandelt werden soll
        String textToSynthesize = "-- -- -- -- -- -- -- ";

        // JSON-Payload entsprechend dem Beispiel
        String jsonPayload = "{"
                + "\"text\": \"" + textToSynthesize + "\","
                + "\"model_id\": \"eleven_multilingual_v2\""
                + "}";

        // Baue die URL inklusive Query-Parameter
        String url = "https://api.elevenlabs.io/v1/text-to-speech/" + voiceId
                + "/stream?output_format=" + outputFormat;

        // Erzeuge einen HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // Erzeuge den HttpRequest
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            // Sende den Request und erhalte die Antwort als byte[]
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                byte[] audioBytes = response.body();

                // Speichern der MP3-Datei
                Files.write(Paths.get("output.mp3"), audioBytes);
                System.out.println("MP3-Datei wurde als output.mp3 gespeichert.");

                // MP3 direkt abspielen
                playMP3(audioBytes);

            } else {
                System.out.println("Fehler: " + response.statusCode());
                System.out.println(new String(response.body()));
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Spielt die MP3-Audiodaten aus dem übergebenen Byte-Array ab.
     * Dafür wird die JLayer-Bibliothek verwendet.
     *
     * @param mp3Bytes Das MP3-Audio-Fragment als Byte-Array.
     */
    private static void playMP3(byte[] mp3Bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(mp3Bytes)) {
            Player player = new Player(bais);
            System.out.println("MP3 wird abgespielt...");
            player.play();  // Dies blockiert, bis das Audio fertig ist
            System.out.println("Wiedergabe beendet.");
        } catch (JavaLayerException | IOException e) {
            e.printStackTrace();
        }
    }
}