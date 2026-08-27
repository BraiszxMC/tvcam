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
 * <p>Cada monitor es un framebuffer propio de 256x144 y el juego dibuja el mundo
 * <b>directamente dentro de el</b>: en los frames de monitor se le da este destino
 * en lugar del suyo. No hay ninguna copia de por medio, que es justo donde fallaba
 * antes (la copia del juego respeta el canal alfa, y el render del mundo lo deja a
 * cero, asi que no copiaba nada). Ademas sale mas barato: se renderiza a 256x144
 * en vez de a pantalla completa.
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

    /** El destino donde el juego debe dibujar el mundo para este monitor. */
    public Framebuffer target(int index) {
        return slot(index).buffer;
    }

    /** Se llama al acabar el frame de un monitor: ya lleva imagen. */
    public void markDrawn(int index) {
        if (index >= 0 && index < slots.size()) {
            slots.get(index).hasImage = true;
        }
    }

    /** El framebuffer de un monitor, para poder inspeccionarlo en la autoprueba. */
    public net.minecraft.client.gl.Framebuffer buffer(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index).buffer : null;
    }

    /** Solo para la autoprueba: pinta un monitor de un color plano. */
    public void fillWithColor(int index, int argb) {
        Slot slot = slot(index);
        if (slot.buffer == null) {
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .clearColorTexture(slot.buffer.getColorAttachment(), argb);
        slot.hasImage = true;
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
            // Con profundidad: el mundo no se puede dibujar sin buffer de profundidad.
            slot.buffer = new SimpleFramebuffer("TVCam preview " + index, WIDTH, HEIGHT, true);
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
