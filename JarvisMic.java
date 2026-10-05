package com.example.jarvis;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Microfono siempre activo: reconocimiento de voz local con Vosk (sin nube). */
public class JarvisMic {
    public interface Vosk extends Library {
        Pointer vosk_model_new(String path);
        Pointer vosk_recognizer_new(Pointer model, float rate);
        int vosk_recognizer_accept_waveform(Pointer rec, byte[] data, int len);
        String vosk_recognizer_result(Pointer rec);
        String vosk_recognizer_partial_result(Pointer rec);
        void vosk_set_log_level(int level);
    }

    static final String JAR_URL = "https://repo1.maven.org/maven2/com/alphacephei/vosk/0.3.45/vosk-0.3.45.jar";
    static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip";

    /** Texto corto de estado para el HUD (vacio = no mostrar). */
    public static volatile String status = "";
    public static volatile boolean listening = false;
    private static volatile boolean started = false;
    private static volatile long awakeUntil = 0;

    /** Palabra de activacion y sus confusiones tipicas del reconocedor. */
    static final Pattern WAKE = Pattern.compile(
            "^\\s*(?:oye\\s+|hey\\s+|ok\\s+|hola\\s+)?(?:jarvis|yarvis|jarbis|yarbis|harvis|jervis|yervis|jarvi|charvis|chavis|garvis|jarvez|yarves|jarves|jarvys|jarbi|yarvi)\\b[\\s,:;.!?-]*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static synchronized void start() {
        if (started || !JarvisMod.MIC_ON.get()) return;
        started = true;
        Thread t = new Thread(JarvisMic::run, "jarvis-microfono");
        t.setDaemon(true);
        t.start();
    }

    static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("jarvis");
    }

    static void run() {
        try {
            Path base = dir();
            Files.createDirectories(base);
            Path libs = base.resolve("vosk-nativos");
            Path model = base.resolve("modelo-es");
            if (!Files.exists(libs.resolve("libvosk.dll"))) {
                status = "descargando motor de voz...";
                JarvisClient.say("Jarvis: Descargando el motor de reconocimiento de voz (una sola vez), senor.", 0x99CCFF);
                downloadNatives(libs);
            }
            if (!Files.exists(model.resolve("am")) && !Files.exists(model.resolve("conf"))) {
                status = "descargando modelo en espanol...";
                JarvisClient.say("Jarvis: Descargando el modelo de voz en espanol (unos 40 MB, una sola vez).", 0x99CCFF);
                downloadModel(base, model);
            }
            status = "cargando voz...";
            for (String d : new String[]{"libwinpthread-1.dll", "libgcc_s_seh-1.dll", "libstdc++-6.dll"}) {
                Path p = libs.resolve(d);
                if (Files.exists(p)) {
                    try { System.load(p.toAbsolutePath().toString()); } catch (Throwable ignored) { }
                }
            }
            Vosk vosk = Native.load(libs.resolve("libvosk.dll").toAbsolutePath().toString(), Vosk.class,
                    java.util.Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
            vosk.vosk_set_log_level(-1);
            Pointer m = vosk.vosk_model_new(model.toAbsolutePath().toString());
            if (m == null) throw new IllegalStateException("no se pudo cargar el modelo de voz");
            Pointer rec = vosk.vosk_recognizer_new(m, 16000f);
            loop(vosk, rec);
        } catch (Throwable t) {
            status = "";
            listening = false;
            JarvisClient.say("Jarvis: Microfono no disponible: " + t, 0xFF6666);
            t.printStackTrace();
        }
    }

    static void loop(Vosk vosk, Pointer rec) throws Exception {
        AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
        TargetDataLine line;
        String dev = JarvisMod.MIC_DEVICE.get().trim();
        if (!dev.isEmpty()) {
            line = null;
            for (var mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().toLowerCase().contains(dev.toLowerCase())) {
                    var mx = AudioSystem.getMixer(mi);
                    if (mx.isLineSupported(new DataLine.Info(TargetDataLine.class, fmt))) {
                        line = (TargetDataLine) mx.getLine(new DataLine.Info(TargetDataLine.class, fmt));
                        break;
                    }
                }
            }
            if (line == null) line = AudioSystem.getTargetDataLine(fmt);
        } else {
            line = AudioSystem.getTargetDataLine(fmt);
        }
        line.open(fmt, 16000);
        line.start();
        listening = true;
        status = "escuchando";
        JarvisClient.say("Jarvis: Microfono activo. Diga \"Jarvis\" seguido de su pedido, senor.", 0x66E0FF);
        byte[] buf = new byte[3200];
        long mutedUntil = 0;
        boolean wasSpeaking = false;
        while (true) {
            int n = line.read(buf, 0, buf.length);
            if (n <= 0) continue;
            if (JarvisVoice.speaking) {
                wasSpeaking = true;
                continue;
            }
            if (wasSpeaking) {
                wasSpeaking = false;
                mutedUntil = System.currentTimeMillis() + 700;
            }
            if (System.currentTimeMillis() < mutedUntil) continue;
            if (vosk.vosk_recognizer_accept_waveform(rec, buf, n) != 0) {
                String text = field(vosk.vosk_recognizer_result(rec), "text");
                if (!text.isEmpty()) onText(text);
            }
        }
    }

    static String field(String json, String key) {
        if (json == null) return "";
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1).trim() : "";
    }

    static void onText(String text) {
        long now = System.currentTimeMillis();
        Matcher m = WAKE.matcher(text);
        String q = null;
        if (m.matches()) {
            q = m.group(1).trim();
            if (q.isEmpty()) {
                awakeUntil = now + JarvisMod.AWAKE_SECONDS.get() * 1000L;
                JarvisClient.say("Jarvis: Digame, senor.", 0x66E0FF);
                return;
            }
        } else if (now < awakeUntil) {
            q = text;
        }
        if (q == null || q.isEmpty()) return;
        awakeUntil = 0;
        final String question = q;
        Minecraft.getInstance().execute(() -> JarvisClient.ask(question));
    }

    // ---------- descargas ----------

    static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "JarvisMod/1.0");
        return c;
    }

    static void downloadNatives(Path libs) throws IOException {
        Files.createDirectories(libs);
        HttpURLConnection c = open(JAR_URL);
        try (ZipInputStream z = new ZipInputStream(c.getInputStream())) {
            ZipEntry e;
            int found = 0;
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                if (!e.isDirectory() && n.startsWith("win32-x86-64/") && n.endsWith(".dll")) {
                    Files.copy(z, libs.resolve(n.substring(n.lastIndexOf('/') + 1)), StandardCopyOption.REPLACE_EXISTING);
                    found++;
                }
            }
            if (found == 0) throw new IOException("el paquete de Vosk no contiene librerias de Windows");
        }
    }

    static void downloadModel(Path base, Path model) throws IOException {
        Path tmp = base.resolve("modelo-tmp");
        deleteTree(tmp);
        Files.createDirectories(tmp);
        HttpURLConnection c = open(MODEL_URL);
        String root = null;
        try (ZipInputStream z = new ZipInputStream(c.getInputStream())) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                Path out = tmp.resolve(e.getName()).normalize();
                if (!out.startsWith(tmp)) continue;
                if (root == null) root = e.getName().split("/")[0];
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(z, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        if (root == null) throw new IOException("modelo vacio");
        deleteTree(model);
        Files.move(tmp.resolve(root), model);
        deleteTree(tmp);
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
                try { Files.delete(x); } catch (IOException ignored) { }
            });
        }
    }
}
