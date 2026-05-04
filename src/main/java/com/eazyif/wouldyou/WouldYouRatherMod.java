package com.eazyif.wouldyou;

import com.eazyif.wouldyou.ai.AIClient;
import com.eazyif.wouldyou.config.ModConfig;
import com.eazyif.wouldyou.effects.EffectMapper;
import com.eazyif.wouldyou.network.ChoicePayload;
import com.eazyif.wouldyou.network.QuestionPayload;
import com.eazyif.wouldyou.question.Question;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side entrypoint. Owns the per-player timer, drives AI question
 * generation, and applies effects when the client returns a choice.
 */
public class WouldYouRatherMod implements ModInitializer {

    public static final String MOD_ID = "wouldyou";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Per-player state: when the next question is due and the active question. */
    private static final class PlayerState {
        long nextTickDue;
        Question pending;        // question already shown to client, awaiting choice
        boolean awaitingFetch;   // a request is in flight
    }

    private final Map<UUID, PlayerState> states = new HashMap<>();

    @Override
    public void onInitialize() {
        ModConfig.get(); // force load / write defaults

        PayloadTypeRegistry.playS2C().register(QuestionPayload.ID, QuestionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChoicePayload.ID, ChoicePayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                onJoin(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                states.remove(handler.getPlayer().getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(this::onTick);

        ServerPlayNetworking.registerGlobalReceiver(ChoicePayload.ID, (payload, ctx) ->
                onChoice(ctx.player(), payload.optionIndex()));

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                LOGGER.info("Would You Rather mod ready (interval={}s, provider={})",
                        ModConfig.get().intervalSeconds, ModConfig.get().provider));
    }

    private void onJoin(ServerPlayerEntity player) {
        PlayerState st = new PlayerState();
        // Start countdown from join. Use the server tick clock for scheduling.
        st.nextTickDue = player.getServer() != null
                ? player.getServer().getTicks() + intervalTicks()
                : intervalTicks();
        states.put(player.getUuid(), st);
    }

    private int intervalTicks() {
        return Math.max(20, ModConfig.get().intervalSeconds * 20);
    }

    private void onTick(MinecraftServer server) {
        long now = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerState st = states.computeIfAbsent(player.getUuid(), k -> {
                PlayerState s = new PlayerState();
                s.nextTickDue = now + intervalTicks();
                return s;
            });
            if (st.pending != null || st.awaitingFetch) continue;
            if (now >= st.nextTickDue) {
                st.awaitingFetch = true;
                UUID id = player.getUuid();
                AIClient.fetch().whenComplete((q, err) -> server.execute(() -> {
                    PlayerState s2 = states.get(id);
                    if (s2 == null) return;
                    s2.awaitingFetch = false;
                    ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
                    if (online == null) return;
                    Question question = (q != null) ? q : new Question(
                            "Would you rather... (error)",
                            "Get a diamond",
                            "Get an emerald");
                    s2.pending = question;
                    s2.nextTickDue = server.getTicks() + intervalTicks();
                    ServerPlayNetworking.send(online,
                            new QuestionPayload(question.question(), question.optionA(), question.optionB()));
                }));
            }
        }
    }

    private void onChoice(ServerPlayerEntity player, int index) {
        PlayerState st = states.get(player.getUuid());
        if (st == null || st.pending == null) return;
        Question q = st.pending;
        st.pending = null;

        String chosen = (index == 0) ? q.optionA() : q.optionB();
        String summary = EffectMapper.apply(player, chosen);
        if (ModConfig.get().announceEffects) {
            player.sendMessage(EffectMapper.describe(summary), false);
        }
    }
}
