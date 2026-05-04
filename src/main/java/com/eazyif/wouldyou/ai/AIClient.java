package com.eazyif.wouldyou.ai;

import com.eazyif.wouldyou.config.ModConfig;
import com.eazyif.wouldyou.question.Question;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Generates a Would-You-Rather question by calling an external AI provider.
 * Currently supports OpenAI Chat Completions and Anthropic Messages APIs.
 */
public final class AIClient {

    private static final String SYSTEM_PROMPT =
            "You generate grounded, realistic Minecraft 'Would You Rather' dilemmas that feel "
          + "like situations a survival player might actually face. Both options must be "
          + "balanced: a modest positive paired with a modest negative — never overpowered, "
          + "never game-ruining. Use vanilla-feeling effect strengths (think potion-tier, not "
          + "godmode). Keep item counts small and believable (1-8 of any item). Mob spawns "
          + "should be 1-3 and survivable. Avoid silly, surreal, meta, or joke scenarios. "
          + "Phrase options plainly, like a player describing a mod's effect (e.g. "
          + "'Gain Speed II for 5 minutes but suffer Mining Fatigue', "
          + "'Receive 3 diamonds but spawn 1 zombie nearby'). "
          + "Use ONLY these effect keywords so the game can map them: speed, jump boost, "
          + "night vision, regeneration, strength, resistance, fire resistance, water breathing, "
          + "haste, luck, glowing, slowness, weakness, hunger, blindness, poison, mining fatigue, "
          + "nausea, levitation; rewards/penalties: diamonds, emeralds, gold, iron, arrows, "
          + "food (cooked beef); spawns: spawn creepers, spawn zombies, spawn skeletons; "
          + "misc: lightning, set on fire, less health, more health, full heal. "
          + "Each option's text MUST contain at least one positive keyword and one negative keyword. "
          + "Reply ONLY with compact JSON, no prose: "
          + "{\"question\":\"Would you rather...\",\"optionA\":{\"text\":\"...\"},\"optionB\":{\"text\":\"...\"}}.";

    private static final String USER_PROMPT =
            "Generate one realistic, balanced Minecraft would-you-rather question with exactly "
          + "2 options. Each option needs one small positive and one small negative gameplay effect "
          + "drawn from the allowed keyword list. Return JSON only.";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private AIClient() {}

    /**
     * Asynchronously fetch a question. Returns a fallback question if the API key
     * is missing or the call fails — the mod should never silently break gameplay.
     */
    public static CompletableFuture<Question> fetch() {
        ModConfig cfg = ModConfig.get();
        if (cfg.apiKey == null || cfg.apiKey.isBlank()) {
            return CompletableFuture.completedFuture(fallback("No API key configured."));
        }
        try {
            return switch (cfg.provider == null ? "openai" : cfg.provider.toLowerCase()) {
                case "anthropic" -> callAnthropic(cfg);
                default -> callOpenAI(cfg);
            };
        } catch (Exception e) {
            return CompletableFuture.completedFuture(fallback(e.getMessage()));
        }
    }

    private static CompletableFuture<Question> callOpenAI(ModConfig cfg) {
        String endpoint = cfg.endpoint == null || cfg.endpoint.isBlank()
                ? "https://api.openai.com/v1/chat/completions"
                : cfg.endpoint;
        String model = cfg.model == null || cfg.model.isBlank() ? "gpt-4o-mini" : cfg.model;

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.6);
        body.add("messages", JsonParser.parseString(
                "[{\"role\":\"system\",\"content\":" + quote(SYSTEM_PROMPT) + "},"
              + "{\"role\":\"user\",\"content\":" + quote(USER_PROMPT) + "}]"));
        body.add("response_format", JsonParser.parseString("{\"type\":\"json_object\"}"));

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    if (r.statusCode() / 100 != 2) {
                        return fallback("OpenAI HTTP " + r.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(r.body()).getAsJsonObject();
                    String content = json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                    return parseQuestionJson(content);
                })
                .exceptionally(t -> fallback(t.getMessage()));
    }

    private static CompletableFuture<Question> callAnthropic(ModConfig cfg) {
        String endpoint = cfg.endpoint == null || cfg.endpoint.isBlank()
                ? "https://api.anthropic.com/v1/messages"
                : cfg.endpoint;
        String model = cfg.model == null || cfg.model.isBlank() ? "claude-haiku-4-5-20251001" : cfg.model;

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 400);
        body.addProperty("system", SYSTEM_PROMPT);
        body.add("messages", JsonParser.parseString(
                "[{\"role\":\"user\",\"content\":" + quote(USER_PROMPT) + "}]"));

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-api-key", cfg.apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    if (r.statusCode() / 100 != 2) {
                        return fallback("Anthropic HTTP " + r.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(r.body()).getAsJsonObject();
                    String text = json.getAsJsonArray("content")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString();
                    return parseQuestionJson(text);
                })
                .exceptionally(t -> fallback(t.getMessage()));
    }

    private static Question parseQuestionJson(String raw) {
        try {
            String trimmed = raw.trim();
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return fallback("AI returned non-JSON content");
            }
            JsonObject obj = JsonParser.parseString(trimmed.substring(start, end + 1)).getAsJsonObject();
            String q = obj.has("question") ? obj.get("question").getAsString() : "Would you rather...";
            String a = textField(obj, "optionA");
            String b = textField(obj, "optionB");
            return new Question(q, a, b);
        } catch (Exception e) {
            return fallback("Failed to parse AI response: " + e.getMessage());
        }
    }

    private static String textField(JsonObject obj, String name) {
        if (!obj.has(name)) return "(missing)";
        var el = obj.get(name);
        if (el.isJsonObject() && el.getAsJsonObject().has("text")) {
            return el.getAsJsonObject().get("text").getAsString();
        }
        return el.isJsonPrimitive() ? el.getAsString() : el.toString();
    }

    private static Question fallback(String reason) {
        return new Question(
                "Would you rather... (offline fallback: " + reason + ")",
                "Gain speed boost but take slowness when standing still",
                "Receive 5 diamonds but spawn 2 creepers nearby");
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
