package com.example.jarvis;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JarvisBrain {
    private static final List<String[]> HISTORY = new ArrayList<>();

    public static synchronized String ask(String user, String context) throws Exception {
        String base = JarvisMod.OLLAMA_URL.get();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        JsonObject body = new JsonObject();
        body.addProperty("model", JarvisMod.MODEL.get());
        body.addProperty("stream", false);
        body.addProperty("keep_alive", "30m");
        JsonObject opts = new JsonObject();
        opts.addProperty("num_predict", 160);
        opts.addProperty("temperature", 0.7);
        body.add("options", opts);
        JsonArray msgs = new JsonArray();
        msgs.add(msg("system", JarvisMod.PROMPT.get() + "\nEstado actual del juego: " + context));
        for (String[] h : HISTORY) msgs.add(msg(h[0], h[1]));
        msgs.add(msg("user", user));
        body.add("messages", msgs);

        HttpURLConnection c = (HttpURLConnection) new URL(base + "/api/chat").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(5000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String txt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (code >= 400) throw new RuntimeException("Ollama respondio " + code + ": " + txt);
        String reply = JsonParser.parseString(txt).getAsJsonObject().getAsJsonObject("message").get("content").getAsString().trim();
        HISTORY.add(new String[] {"user", user});
        HISTORY.add(new String[] {"assistant", reply});
        while (HISTORY.size() > 16) HISTORY.remove(0);
        return reply;
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }
}
