package com.braiszx.tvcam.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Un boton de mesa de realizacion: tecla oscura con su piloto, que se enciende
 * cuando esta activa y se aclara al pasar el raton.
 */
public class ConsoleButton extends ClickableWidget {
    public interface Action {
        void run();
    }

    private final Action action;
    private java.util.function.Supplier<String> label;
    private java.util.function.BooleanSupplier lit = () -> false;
    private int accent = Console.SELECT;
    private boolean compact;
    private String help;

    public ConsoleButton(int x, int y, int width, int height, String text, Action action) {
        super(x, y, width, height, Text.literal(text));
        this.label = () -> text;
        this.action = action;
    }

    public ConsoleButton label(java.util.function.Supplier<String> supplier) {
        this.label = supplier;
        return this;
    }

    /** Cuando devuelve true, la tecla se ve encendida. */
    public ConsoleButton lit(java.util.function.BooleanSupplier supplier) {
        this.lit = supplier;
        return this;
    }

    public ConsoleButton accent(int color) {
        this.accent = color;
        return this;
    }

    /** Explicacion que sale abajo al poner el raton encima. */
    public ConsoleButton help(String text) {
        this.help = text;
        return this;
    }

    public String helpText() {
        return help;
    }

    public ConsoleButton compact() {
        this.compact = true;
        return this;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean on = lit.getAsBoolean();
        boolean hover = isHovered();

        int face = on ? Console.mix(accent, 0.42f) : (hover ? Console.PANEL_RAISED : Console.PANEL);
        Console.panel(context, getX(), getY(), getWidth(), getHeight(), face);
        Console.outline(context, getX(), getY(), getWidth(), getHeight(),
                on ? accent : (hover ? Console.EDGE : Console.EDGE_SOFT));

        // Cinta de color a la izquierda: se ve de un vistazo que teclas estan activas.
        if (!compact) {
            context.fill(getX() + 2, getY() + 3, getX() + 4, getY() + getHeight() - 3,
                    on ? accent : Console.EDGE_SOFT);
        }

        String text = label.get();
        int textColor = on ? Console.TEXT : (hover ? Console.TEXT : Console.TEXT_DIM);
        int textY = getY() + (getHeight() - 8) / 2;
        if (compact) {
            context.drawCenteredTextWithShadow(Console.font(), text,
                    getX() + getWidth() / 2, textY, textColor);
        } else {
            int available = getWidth() - 12;
            String shown = Console.font().trimToWidth(text, available);
            context.drawText(Console.font(), shown, getX() + 8, textY, textColor, false);
        }
    }

    @Override
    public void onClick(net.minecraft.client.gui.Click click, boolean doubled) {
        action.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
