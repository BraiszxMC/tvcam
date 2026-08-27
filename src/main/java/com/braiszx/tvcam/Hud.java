package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraMode;
import com.braiszx.tvcam.camera.CameraPoint;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/** Un aviso en tu ventana (nunca en la emision) de que plano esta al aire. */
public final class Hud {
    private static final int RED = 0xFFFF5555;
    private static final int GREY = 0xFFBBBBBB;

    private Hud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            CameraDirector director = CameraDirector.get();
            if (director.isCameraFrame()) {
                return;
            }
            CameraPoint camera = director.activeCamera();
            if (camera == null || !director.window().isOpen()) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            String live = "● AL AIRE  CAM " + (director.activeIndex() + 1) + " · " + camera.name();
            context.drawTextWithShadow(client.textRenderer, live, 6, 6, RED);

            StringBuilder detail = new StringBuilder(camera.mode().name().toLowerCase());
            if (camera.zoom() > 1.01f) {
                detail.append(String.format("  ·  zoom x%.2f", camera.zoom()));
            }
            if (camera.mode() != CameraMode.FIJA) {
                detail.append("  ·  ").append(director.target().describe());
            }
            if (director.settings().autoDirector) {
                detail.append("  ·  auto");
            }
            context.drawTextWithShadow(client.textRenderer, detail.toString(), 6, 18, GREY);
        });
    }
}
