package com.eazyif.wouldyou.network;

import com.eazyif.wouldyou.WouldYouRatherMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client -> server: the player's choice. The server still has the original
 * option texts cached, so the client only needs to indicate which one.
 *
 * @param optionIndex 0 = A, 1 = B
 */
public record ChoicePayload(int optionIndex) implements CustomPayload {

    public static final CustomPayload.Id<ChoicePayload> ID =
            new CustomPayload.Id<>(Identifier.of(WouldYouRatherMod.MOD_ID, "choice"));

    public static final PacketCodec<PacketByteBuf, ChoicePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ChoicePayload::optionIndex,
            ChoicePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
