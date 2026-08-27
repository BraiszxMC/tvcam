package com.braiszx.tvcam.render;

import com.braiszx.tvcam.TVCam;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Las miniaturas de cada camara para el multiviewer de la mesa.
 *
 * <p>Una miniatura no se puede "capturar" sin mas: el mundo hay que dibujarlo
 * desde esa camara. Por eso el realizador dedica de vez en cuando un frame a una
 * camara concreta, y aqui se guarda el resultado reducido. Cada camara se refresca
 * una vez cada pocas decimas, que para un monitor de control sobra.
 */
public final class PreviewBank {
    /** Tamano de cada miniatura. Pequeno a proposito: son monitores de control. */
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;

    private final List<Slot> slots = new ArrayList<>();

    /** Una miniatura: su framebuffer y la textura con la que se dibuja en la interfaz. */
    private static final class Slot {
        SimpleFramebuffer buffer;
        Identifier id;
        boolean hasImage;
    }

    /** Se queda con la imagen del frame actual, reducida, para la camara indicada. */
    public void capture(int index, Framebuffer source) {
        Slot slot = slot(index);
        if (slot.buffer == null) {
            return;
        }
        // Ojo con el sentido: framebuffer.drawBlit(destino) dibuja la textura DEL
        // framebuffer DENTRO del destino. Es decir, hay que llamarlo sobre la
        // imagen del juego pasandole el monitor, y no al reves. Al reves se pinta
        // el monitor vacio encima del juego, que era el origen de la basura que
        // salia en la emision.
        source.drawBlit(slot.buffer.getColorAttachmentView());
        slot.hasImage = true;
    }

    /** El framebuffer de un monitor, para poder inspeccionarlo en la autoprueba. */
    public net.minecraft.client.gl.Framebuffer buffer(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index).buffer : null;
    }

    public boolean hasImage(int index) {
        return index >= 0 && index < slots.size() && slots.get(index).hasImage;
    }

    /** La textura con la que dibujar la miniatura, o null si aun no hay imagen. */
    public Identifier texture(int index) {
        if (!hasImage(index)) {
            return null;
        }
        return slots.get(index).id;
    }

    private Slot slot(int index) {
        while (slots.size() <= index) {
            slots.add(create(slots.size()));
        }
        return slots.get(index);
    }

    private Slot create(int index) {
        Slot slot = new Slot();
        try {
            slot.buffer = new SimpleFramebuffer("TVCam preview " + index, WIDTH, HEIGHT, false);
            slot.id = Identifier.of(TVCam.MOD_ID, "preview/" + index);
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(slot.id, new PreviewTexture(slot.buffer));
        } catch (RuntimeException e) {
            TVCam.LOGGER.error("No se pudo crear el monitor de la camara {}", index + 1, e);
            slot.buffer = null;
        }
        return slot;
    }

    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Slot slot : slots) {
            if (slot.id != null) {
                client.getTextureManager().destroyTexture(slot.id);
            }
            if (slot.buffer != null) {
                slot.buffer.delete();
            }
        }
        slots.clear();
    }

    /**
     * Envuelve el framebuffer de una miniatura como textura del juego, para poder
     * dibujarla en la interfaz igual que cualquier otra.
     */
    private static final class PreviewTexture extends AbstractTexture {
        PreviewTexture(Framebuffer framebuffer) {
            this.glTexture = framebuffer.getColorAttachment();
            this.glTextureView = framebuffer.getColorAttachmentView();
            this.sampler = RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
        }

        @Override
        public void close() {
            // El framebuffer es de PreviewBank: aqui no se cierra nada prestado.
        }
    }
}
