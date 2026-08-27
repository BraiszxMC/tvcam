package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraMode;
import com.braiszx.tvcam.camera.CameraPoint;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * El aviso de que estas emitiendo, en tu pantalla y nunca en la emision: una
 * chapita con el piloto rojo, el plano al aire y, debajo, en pequeño, como esta
 * configurado.
 */
public final class Hud {
    private static final int BACKGROUND = 0xB0101418;
    private static final int TALLY = 0xFFFF4444;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF9AA6B4;

    private Hud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            CameraDirector director = CameraDirector.get();
            if (director.isBroadcastFrame()) {
                return;
            }
            CameraPoint camera = director.activeCamera();
            if (camera == null || !director.window().isOpen()) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            var font = client.textRenderer;

            String title = "CAM " + (director.activeIndex() + 1) + "  " + camera.name();
            String detail = detail(director, camera);

            int padding = 5;
            int width = Math.max(font.getWidth(title) + 16, font.getWidth(detail) + 10) + padding * 2;
            int height = 26;
            int x = 6;
            int y = 6;

            // Chapa con las esquinas mordidas, a juego con la mesa.
            context.fill(x + 2, y, x + width - 2, y + height, BACKGROUND);
            context.fill(x, y + 2, x + 2, y + height - 2, BACKGROUND);
            context.fill(x + width - 2, y + 2, x + width, y + height - 2, BACKGROUND);
            // Filo rojo a la izquierda: se ve de reojo que estas al aire.
            context.fill(x + 2, y + 2, x + 4, y + height - 2, TALLY);

            int textX = x + padding + 4;
            context.fill(textX, y + 6, textX + 4, y + 10, TALLY);
            context.drawTextWithShadow(font, title, textX + 8, y + 5, TEXT);
            context.drawText(font, detail, textX, y + 16, TEXT_DIM, false);
        });
    }

    private static String detail(CameraDirector director, CameraPoint camera) {
        StringBuilder detail = new StringBuilder();
        detail.append(switch (camera.mode()) {
            case FIJA -> "plano fijo";
            case SEGUIR -> "gira siguiendo";
            case ACOMPANAR -> "va detras";
        });
        if (camera.mode() != CameraMode.FIJA) {
            detail.append(" a ").append(camera.target().shortLabel());
        }
        float zoom = director.currentZoom();
        if (zoom > 1.01f) {
            detail.append(String.format("  ·  zoom x%.1f", zoom));
        }
        if (director.settings().autoDirector) {
            detail.append("  ·  auto");
        }
        return detail.toString();
    }
}
