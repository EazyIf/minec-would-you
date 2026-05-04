package com.eazyif.wouldyou.client;

import com.eazyif.wouldyou.network.QuestionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side entrypoint. Listens for {@link QuestionPayload} from the server
 * and opens the choice screen.
 */
public class WouldYouRatherClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(QuestionPayload.ID, (payload, ctx) -> {
            MinecraftClient client = ctx.client();
            client.execute(() -> client.setScreen(
                    new WouldYouRatherScreen(payload.question(), payload.optionA(), payload.optionB())));
        });
    }
}
