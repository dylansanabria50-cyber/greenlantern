package com.example.jarvis;

import com.google.gson.JsonObject;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JarvisVoice {
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jarvis-voz");
        t.setDaemon(true);
        return t;
    });
    public static volatile boolean speaking = false;

    public static void speak(String text) {
        String key = JarvisMod.ELEVEN_KEY.get().trim();
        String voice = JarvisMod.VOICE_ID.get().trim();
        if (!JarvisMod.SPEAK.get() || key.isEmpty() || voice.isEmpty()) return;
        POOL.submit(() -> {
            speaking = true;
            try {
                JsonObject body = new JsonObject();
                body.addProperty("text", text);
                body.addProperty("model_id", JarvisMod.ELEVEN_MODEL.get());
                HttpURLConnection c = (HttpURLConnection) new URL(
                        "https://api.elevenlabs.io/v1/text-to-speech/" + voice + "?output_format=pcm_24000").openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(60000);
                c.setDoOutput(true);
                c.setRequestProperty("xi-api-key", key);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                if (code >= 400) {
                    String err = new String(c.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    JarvisClient.say("[Voz] ElevenLabs error " + code + ": " + (err.length() > 120 ? err.substring(0, 120) : err), 0xFF6666);
                    return;
                }
                byte[] pcm;
                try (InputStream in = c.getInputStream()) {
                    pcm = in.readAllBytes();
                }
                AudioFormat fmt = new AudioFormat(24000f, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                line.start();
                line.write(pcm, 0, pcm.length);
                line.drain();
                line.close();
            } catch (Throwable t) {
                JarvisClient.say("[Voz] " + t, 0xFF6666);
            } finally {
                speaking = false;
            }
        });
    }
}
