package com.braiszx.tvcam.net;

import com.braiszx.tvcam.TVCam;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * El sobre en el que viajan los mensajes entre el mod y el plugin del servidor:
 * texto plano, sin mas. Cuanto mas tonto el formato, menos cosas que fallen.
 */
public record ControlPayload(String message) implements CustomPayload {
    public static final Identifier CHANNEL = Identifier.of("tvcam", "control");
    public static final CustomPayload.Id<ControlPayload> ID = new CustomPayload.Id<>(CHANNEL);

    public static final PacketCodec<PacketByteBuf, ControlPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeBytes(payload.message().getBytes(StandardCharsets.UTF_8)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new ControlPayload(new String(bytes, StandardCharsets.UTF_8));
            });

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void log(String direction, String message) {
        TVCam.LOGGER.debug("[remoto] {} {}", direction, message);
    }
}
