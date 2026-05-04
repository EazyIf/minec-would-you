package com.eazyif.wouldyou.network;

import com.eazyif.wouldyou.WouldYouRatherMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client: deliver a generated question and the two option texts.
 */
public record QuestionPayload(String question, String optionA, String optionB) implements CustomPayload {

    public static final CustomPayload.Id<QuestionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(WouldYouRatherMod.MOD_ID, "question"));

    public static final PacketCodec<PacketByteBuf, QuestionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QuestionPayload::question,
            PacketCodecs.STRING, QuestionPayload::optionA,
            PacketCodecs.STRING, QuestionPayload::optionB,
            QuestionPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
