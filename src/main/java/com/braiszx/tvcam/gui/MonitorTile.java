package com.braiszx.tvcam.gui;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraPoint;
import com.braiszx.tvcam.render.PreviewBank;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Un monitor: la imagen de esa camara, su numero, su nombre y su piloto de tally.
 * Un clic la selecciona, dos la ponen al aire.
 */
public final class MonitorTile extends ClickableWidget {
    private final int index;
    private final Runnable onSelect;
    private final Runnable onTake;
    private final boolean large;

    public MonitorTile(int x, int y, int width, int height, int index,
                       Runnable onSelect, Runnable onTake, boolean large) {
        super(x, y, width, height, Text.literal("Camara " + (index + 1)));
        this.index = index;
        this.onSelect = onSelect;
        this.onTake = onTake;
        this.large = large;
    }

    /** Dibuja la imagen de una camara dentro de un rectangulo, del derecho. */
    public static boolean drawFeed(DrawContext context, int index, int x, int y, int width, int height) {
        Identifier texture = CameraDirector.get().previews().texture(index);
        if (texture == null) {
            return false;
        }
        // El mundo se dibuja con el origen abajo, la interfaz con el origen arriba:
        // se pide la region con la altura en negativo para darle la vuelta.
        context.drawTexture(RenderPipelines.GUI_OPAQUE_TEX_BG, texture, x, y,
                0.0f, PreviewBank.HEIGHT,
                width, height,
                PreviewBank.WIDTH, -PreviewBank.HEIGHT,
                PreviewBank.WIDTH, PreviewBank.HEIGHT);
        return true;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        CameraDirector director = CameraDirector.get();
        CameraPoint camera = director.at(index);
        boolean live = index == director.activeIndex();
        boolean selected = DeskScreen.selectedIndex() == index;

        int labelHeight = large ? 14 : 12;
        int imageHeight = getHeight() - labelHeight;

        Console.panel(context, getX(), getY(), getWidth(), getHeight(), Console.PANEL_SUNKEN);

        if (!drawFeed(context, index, getX() + 2, getY() + 2, getWidth() - 4, imageHeight - 2)) {
            context.drawCenteredTextWithShadow(Console.font(), "sin senal",
                    getX() + getWidth() / 2, getY() + imageHeight / 2 - 4, Console.TEXT_FAINT);
        }

        int barY = getY() + imageHeight;
        context.fill(getX() + 2, barY, getX() + getWidth() - 2, getY() + getHeight(),
                live ? Console.mix(Console.TALLY, 0.4f) : Console.PANEL);
        Console.tallyLamp(context, getX() + 5, barY + (labelHeight - 5) / 2, live);
        String name = camera == null ? "-" : (index + 1) + "  " + camera.name();
        context.drawText(Console.font(), Console.font().trimToWidth(name, getWidth() - 20),
                getX() + 13, barY + (labelHeight - 7) / 2,
                live ? Console.TEXT : Console.TEXT_DIM, false);

        if (live) {
            String badge = "AL AIRE";
            int badgeWidth = Console.font().getWidth(badge) + 8;
            context.fill(getX() + getWidth() - badgeWidth - 3, getY() + 3,
                    getX() + getWidth() - 3, getY() + 13, Console.TALLY);
            context.drawText(Console.font(), badge, getX() + getWidth() - badgeWidth + 1, getY() + 5,
                    0xFFFFFFFF, false);
        }

        int border = live ? Console.TALLY : (selected ? Console.SELECT
                : (isHovered() ? Console.EDGE : Console.EDGE_SOFT));
        Console.outline(context, getX(), getY(), getWidth(), getHeight(), border);
    }

    @Override
    public void onClick(net.minecraft.client.gui.Click click, boolean doubled) {
        if (doubled) {
            onTake.run();
        } else {
            onSelect.run();
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
