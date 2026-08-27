package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraPoint;
import com.braiszx.tvcam.camera.TargetTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Teclas del numpad, como una mesa de realizacion: 1-9 cortan a cada camara y 0
 * corta la emision.
 */
public final class Keybinds {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(TVCam.MOD_ID, "tvcam"));

    private static final KeyBinding[] CUT = new KeyBinding[9];
    private static KeyBinding stop;
    private static KeyBinding addCamera;
    private static KeyBinding toggleWindow;
    private static KeyBinding zoomIn;
    private static KeyBinding zoomOut;
    private static KeyBinding lockTarget;
    private static KeyBinding autoDirector;

    private Keybinds() {
    }

    public static void register() {
        int[] numpad = {
                GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3,
                GLFW.GLFW_KEY_KP_4, GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_KP_6,
                GLFW.GLFW_KEY_KP_7, GLFW.GLFW_KEY_KP_8, GLFW.GLFW_KEY_KP_9
        };
        for (int i = 0; i < CUT.length; i++) {
            CUT[i] = register("key.tvcam.cut." + (i + 1), numpad[i]);
        }
        stop = register("key.tvcam.stop", GLFW.GLFW_KEY_KP_0);
        addCamera = register("key.tvcam.add", GLFW.GLFW_KEY_KP_MULTIPLY);
        toggleWindow = register("key.tvcam.window", GLFW.GLFW_KEY_KP_DECIMAL);
        zoomIn = register("key.tvcam.zoom_in", GLFW.GLFW_KEY_KP_ADD);
        zoomOut = register("key.tvcam.zoom_out", GLFW.GLFW_KEY_KP_SUBTRACT);
        lockTarget = register("key.tvcam.lock", GLFW.GLFW_KEY_KP_DIVIDE);
        autoDirector = register("key.tvcam.auto", GLFW.GLFW_KEY_KP_ENTER);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CameraDirector director = CameraDirector.get();
            for (int i = 0; i < CUT.length; i++) {
                while (CUT[i].wasPressed()) {
                    director.cut(i);
                }
            }
            while (stop.wasPressed()) {
                director.cut(-1);
            }
            while (addCamera.wasPressed()) {
                director.addHere(null);
            }
            while (toggleWindow.wasPressed()) {
                director.toggleWindow();
            }
            while (zoomIn.wasPressed()) {
                changeZoom(director, 0.25f);
            }
            while (zoomOut.wasPressed()) {
                changeZoom(director, -0.25f);
            }
            while (lockTarget.wasPressed()) {
                lockOn(director, client);
            }
            while (autoDirector.wasPressed()) {
                toggleAuto(director);
            }

            director.tickBroadcast();
            director.tickAutoDirector();
        });
    }

    private static KeyBinding register(String translationKey, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(translationKey, InputUtil.Type.KEYSYM, key, CATEGORY));
    }

    private static void changeZoom(CameraDirector director, float step) {
        CameraPoint camera = director.activeCamera();
        if (camera == null) {
            return;
        }
        float zoom = Math.clamp(camera.zoom() + step, 1.0f, 10.0f);
        director.replace(director.activeIndex(), camera.withZoom(zoom));
        message(String.format("Zoom x%.2f", zoom), Formatting.AQUA);
    }

    private static void lockOn(CameraDirector director, MinecraftClient client) {
        Entity entity = TargetTracker.entityUnderCrosshair(client);
        if (entity != null && director.target().followEntity(entity)) {
            message("Objetivo: " + entity.getName().getString(), Formatting.GREEN);
        } else {
            // Sin nada delante, lo mas util es asumir que quieres la pelota.
            director.target().followBall();
            message("Objetivo: la pelota", Formatting.GREEN);
        }
    }

    private static void toggleAuto(CameraDirector director) {
        boolean enabled = !director.settings().autoDirector;
        director.settings().autoDirector = enabled;
        director.saveSettings();
        message(enabled ? "Realizador automatico ON" : "Realizador automatico OFF",
                enabled ? Formatting.GREEN : Formatting.GRAY);
    }

    private static void message(String text, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("TVCam: " + text).formatted(color), true);
        }
    }
}
