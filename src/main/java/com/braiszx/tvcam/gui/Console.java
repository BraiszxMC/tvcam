package com.braiszx.tvcam.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;

/**
 * La paleta y los trazos de la mesa: negro de rack, paneles grafito, tally rojo
 * para lo que esta al aire y ambar para lo seleccionado. Nada de botones de
 * Minecraft.
 */
public final class Console {
    public static final int BACKDROP = 0xFF0B0D10;
    public static final int PANEL = 0xFF161A21;
    public static final int PANEL_RAISED = 0xFF1E242D;
    public static final int PANEL_SUNKEN = 0xFF0E1116;
    public static final int EDGE = 0xFF2C3540;
    public static final int EDGE_SOFT = 0xFF222A33;

    public static final int TEXT = 0xFFE6EAF0;
    public static final int TEXT_DIM = 0xFF8B96A5;
    public static final int TEXT_FAINT = 0xFF5A6472;

    public static final int TALLY = 0xFFE03A3A;
    public static final int TALLY_DIM = 0xFF5A1F22;
    public static final int SELECT = 0xFFE8A33D;
    public static final int OK = 0xFF3DBE7A;

    private Console() {
    }

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** Rectangulo con esquinas mordidas, que es lo que da el aire de equipo. */
    public static void panel(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 2, y, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + 2, y + height - 2, color);
        context.fill(x + width - 2, y + 2, x + width, y + height - 2, color);
    }

    public static void outline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 2, y, x + width - 2, y + 1, color);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + 1, y + height - 2, color);
        context.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    /** Titulo de seccion con su linea, como la serigrafia de un panel. */
    public static void sectionTitle(DrawContext context, String text, int x, int y, int width) {
        context.drawText(font(), text, x, y, TEXT_FAINT, false);
        int textWidth = font().getWidth(text);
        context.fill(x + textWidth + 6, y + 3, x + width, y + 4, EDGE_SOFT);
    }

    /** Piloto de tally: el circulito rojo que se enciende con lo que sale al aire. */
    public static void tallyLamp(DrawContext context, int x, int y, boolean on) {
        int color = on ? TALLY : TALLY_DIM;
        context.fill(x + 1, y, x + 4, y + 5, color);
        context.fill(x, y + 1, x + 5, y + 4, color);
    }

    public static int mix(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (Math.min(r, 255) << 16) | (Math.min(g, 255) << 8) | Math.min(b, 255);
    }
}
