package com.example.jarvis;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Control de musica con las teclas multimedia de Windows (Spotify, YouTube Music, etc.). Solo eso: no toca archivos. */
public class JarvisMusic {
    // codigos de teclas multimedia de Windows para SendKeys
    static final int PLAY = 179, NEXT = 176, PREV = 177, VOL_UP = 175, VOL_DOWN = 174, MUTE = 173;

    static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    static boolean has(String s, String re) {
        return Pattern.compile(re).matcher(s).find();
    }

    /** Devuelve la respuesta hablada si el pedido es de musica; null si no lo es. */
    public static String tryHandle(String raw) {
        String s = norm(raw);
        int words = s.isEmpty() ? 0 : s.split(" ").length;
        boolean music = has(s, "\\b(musica|cancion|canciones|tema|pista|spotify|reproductor|volumen|sonido)\\b");
        if ((music || words <= 2) && has(s, "\\b(siguiente|proxima|proximo|salta|saltar|adelanta)\\b")) {
            press(NEXT);
            return "Siguiente tema, senor.";
        }
        if ((music || words <= 2) && has(s, "\\b(anterior|previa|previo|retrocede)\\b")) {
            press(PREV);
            return "Tema anterior, senor.";
        }
        if (music && has(s, "\\b(pausa|pausar|deten|detener|para|parar|reanuda|reanudar|continua|continuar|reproduce|reproducir|pon|poner|play|inicia|iniciar|activa)\\b")
                && !has(s, "\\b(volumen|sube|baja|subir|bajar)\\b")) {
            press(PLAY);
            return "Hecho, senor.";
        }
        if (words <= 2 && has(s, "\\b(pausa|pausar|reanuda|reanudar|continua)\\b")) {
            press(PLAY);
            return "Hecho, senor.";
        }
        if (has(s, "\\b(silencia|silenciar|mutea|mutear)\\b")) {
            press(MUTE);
            return "Sonido silenciado, senor.";
        }
        if (has(s, "\\b(sube|subir|aumenta|aumentar)\\b.*\\b(volumen|musica|sonido)\\b|\\bvolumen\\b.*\\b(sube|subir|arriba|mas)\\b")) {
            for (int i = 0; i < 5; i++) press(VOL_UP);
            return "Subiendo el volumen, senor.";
        }
        if (has(s, "\\b(baja|bajar|disminuye|disminuir|reduce|reducir)\\b.*\\b(volumen|musica|sonido)\\b|\\bvolumen\\b.*\\b(baja|bajar|abajo|menos)\\b")) {
            for (int i = 0; i < 5; i++) press(VOL_DOWN);
            return "Bajando el volumen, senor.";
        }
        return null;
    }

    static void press(int code) {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
            new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                    "(New-Object -ComObject WScript.Shell).SendKeys([char]" + code + ")").start();
        } catch (Throwable t) {
            JarvisClient.say("Jarvis: No pude enviar la tecla multimedia: " + t, 0xFF6666);
        }
    }
}
