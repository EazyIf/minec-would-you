package com.eazyif.wouldyou.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON-backed config persisted in <code>config/wouldyou.json</code>.
 * Fields are public so Gson can populate them.
 */
public class ModConfig {

    /** Provider: "openai" or "anthropic". */
    public String provider = "openai";

    /** API key. Leave empty to disable AI calls (mod will use a safe fallback). */
    public String apiKey = "";

    /** Model name. Defaults are sensible per-provider. */
    public String model = "gpt-4o-mini";

    /** Optional endpoint override. Empty = use provider default. */
    public String endpoint = "";

    /** Seconds between questions. Default 10 minutes. */
    public int intervalSeconds = 600;

    /** Whether to also display a chat message describing the applied effect. */
    public boolean announceEffects = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;

    public static ModConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("wouldyou.json");
    }

    public static ModConfig load() {
        Path path = configPath();
        try {
            if (!Files.exists(path)) {
                ModConfig fresh = new ModConfig();
                fresh.save();
                return fresh;
            }
            String text = Files.readString(path);
            ModConfig loaded = GSON.fromJson(text, ModConfig.class);
            return loaded != null ? loaded : new ModConfig();
        } catch (IOException e) {
            return new ModConfig();
        }
    }

    public void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}
