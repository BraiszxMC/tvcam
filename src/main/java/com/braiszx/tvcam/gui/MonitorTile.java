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
 * Un monitor del multiviewer: la imagen de esa camara, su numero, su nombre y su
 * piloto de tally. Un clic la selecciona, dos la ponen al aire.
 */
public final class MonitorTile extends ClickableWidget {
    private final int index;
    private final Runnable onSelect;
    private final Runnable onTake;

    public MonitorTile(int x, int y, int width, int height, int index,
                       Runnable onSelect, Runnable onTake) {
        super(x, y, width, height, Text.literal("Camara " + (index + 1)));
        this.index = index;
        this.onSelect = onSelect;
        this.onTake = onTake;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        CameraDirector director = CameraDirector.get();
        CameraPoint camera = director.at(index);
        boolean live = index == director.activeIndex();
        boolean selected = onSelectIsCurrent();

        int border = live ? Console.TALLY : (selected ? Console.SELECT
                : (isHovered() ? Console.EDGE : Console.EDGE_SOFT));

        int labelHeight = 12;
        int imageHeight = getHeight() - labelHeight;

        Console.panel(context, getX(), getY(), getWidth(), getHeight(), Console.PANEL_SUNKEN);

        Identifier texture = director.previews().texture(index);
        if (texture != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    getX() + 2, getY() + 2, 0.0f, 0.0f,
                    getWidth() - 4, imageHeight - 2,
                    PreviewBank.WIDTH, PreviewBank.HEIGHT);
        } else {
            String waiting = "sin senal";
            context.drawCenteredTextWithShadow(Console.font(), waiting,
                    getX() + getWidth() / 2, getY() + imageHeight / 2 - 4, Console.TEXT_FAINT);
        }

        // Barra inferior con numero, nombre y tally.
        int barY = getY() + imageHeight;
        context.fill(getX() + 2, barY, getX() + getWidth() - 2, getY() + getHeight(),
                live ? Console.mix(Console.TALLY, 0.35f) : Console.PANEL);
        Console.tallyLamp(context, getX() + 5, barY + 4, live);
        String name = camera == null ? "-" : (index + 1) + " " + camera.name();
        context.drawText(Console.font(),
                Console.font().trimToWidth(name, getWidth() - 20), getX() + 13, barY + 2,
                live ? Console.TEXT : Console.TEXT_DIM, false);

        Console.outline(context, getX(), getY(), getWidth(), getHeight(), border);
    }

    private boolean onSelectIsCurrent() {
        return DeskScreen.selectedIndex() == index;
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
