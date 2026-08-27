package com.braiszx.tvcam.broadcast;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

/**
 * El rotulo de gol que aparece sobre la emision: un GOL grande, quien lo ha
 * marcado y el resultado, con entrada y salida suaves.
 *
 * <p>Solo se dibuja en los frames de camara, es decir, en la ventana que captura
 * OBS. En tu pantalla sigues viendo el juego limpio.
 */
public final class GoalFlash {
    private static final long DURATION_NANOS = 4_500_000_000L;
    private static final long FADE_NANOS = 500_000_000L;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int SHADOW_BAR = 0xB0000000;

    private String scorer;
    private String score;
    private long startNanos;

    public void show(String scorer, String score) {
        this.scorer = scorer;
        this.score = score;
        this.startNanos = System.nanoTime();
    }

    public boolean isShowing() {
        return startNanos != 0L && System.nanoTime() - startNanos < DURATION_NANOS;
    }

    public String scorer() {
        return scorer;
    }

    public void render(DrawContext context) {
        if (!isShowing()) {
            return;
        }
        long elapsed = System.nanoTime() - startNanos;
        float alpha = 1.0f;
        if (elapsed < FADE_NANOS) {
            alpha = elapsed / (float) FADE_NANOS;
        } else if (elapsed > DURATION_NANOS - FADE_NANOS) {
            alpha = (DURATION_NANOS - elapsed) / (float) FADE_NANOS;
        }
        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);

        String headline = CameraDirector.get().settings().goalText;
        if (headline == null || headline.isBlank()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int centerX = width / 2;
        int baseY = (int) (height * 0.32);

        // Una banda oscura detras, para que el texto se lea sobre cualquier fondo.
        context.fill(0, baseY - 8, width, baseY + 58, withAlpha(SHADOW_BAR, alpha));

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, baseY);
        // La entrada tiene un pequeno golpe de escala, como un rotulo de television.
        float pop = 1.0f + (1.0f - alpha) * 0.15f;
        matrices.scale(4.0f * pop, 4.0f * pop);
        context.drawCenteredTextWithShadow(client.textRenderer, headline, 0, 0, withAlpha(WHITE, alpha));
        matrices.popMatrix();

        int textY = baseY + 40;
        if (scorer != null && !scorer.isBlank()) {
            matrices.pushMatrix();
            matrices.translate(centerX, textY);
            matrices.scale(1.5f, 1.5f);
            context.drawCenteredTextWithShadow(client.textRenderer, scorer, 0, 0, withAlpha(WHITE, alpha));
            matrices.popMatrix();
            textY += 18;
        }
        if (score != null && !score.isBlank()) {
            context.drawCenteredTextWithShadow(client.textRenderer, score, centerX, textY,
                    withAlpha(0xFFDDDDDD, alpha));
        }
    }

    private static int withAlpha(int color, float alpha) {
        int base = (int) ((color >>> 24) * alpha);
        return (base << 24) | (color & 0x00FFFFFF);
    }

    /** Atajo para lanzar el rotulo desde cualquier sitio. */
    public static void trigger(String scorer, String score) {
        CameraDirector.get().broadcast().goalFlash().show(scorer, score);
    }
}
