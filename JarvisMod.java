package com.example.jarvis;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("jarvis")
public class JarvisMod {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_URL;
    public static final ForgeConfigSpec.ConfigValue<String> MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> ELEVEN_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> VOICE_ID;
    public static final ForgeConfigSpec.ConfigValue<String> ELEVEN_MODEL;
    public static final ForgeConfigSpec.BooleanValue SPEAK;
    public static final ForgeConfigSpec.ConfigValue<String> PROMPT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("cerebro");
        OLLAMA_URL = b.comment("Direccion de Ollama").define("ollama_url", "http://localhost:11434");
        MODEL = b.comment("Modelo de Ollama (ejecute: ollama pull llama3.1:8b)").define("modelo", "llama3.1:8b");
        PROMPT = b.comment("Personalidad de Jarvis").define("personalidad",
                "Eres J.A.R.V.I.S., el asistente de inteligencia artificial del senor, inspirado en el de Iron Man. "
                + "Hablas en espanol, con tono educado, elegante y con un toque de ironia fina. Llamas al usuario 'senor'. "
                + "Estas dentro del juego Minecraft y ves el estado del jugador que se te indica. "
                + "Responde SIEMPRE breve: maximo dos frases cortas, sin listas, sin markdown, sin emojis.");
        b.pop();
        b.push("voz");
        SPEAK = b.comment("Hablar en voz alta con ElevenLabs").define("hablar", true);
        ELEVEN_KEY = b.comment("Clave de API de ElevenLabs (xi-api-key)").define("elevenlabs_clave", "");
        VOICE_ID = b.comment("ID de la voz de ElevenLabs que quiera usar para Jarvis").define("elevenlabs_voz_id", "");
        ELEVEN_MODEL = b.comment("Modelo de voz de ElevenLabs").define("elevenlabs_modelo", "eleven_multilingual_v2");
        b.pop();
        SPEC = b.build();
    }

    public JarvisMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "jarvis-client.toml");
        if (FMLEnvironment.dist.isClient()) {
            JarvisClient.init();
        }
    }
}
