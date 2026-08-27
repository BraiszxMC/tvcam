package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraPoint;
import com.braiszx.tvcam.camera.TargetTracker;
import com.braiszx.tvcam.gui.DeskScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
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
    private static KeyBinding desk;

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
        desk = register("key.tvcam.desk", GLFW.GLFW_KEY_M);

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

            while (desk.wasPressed()) {
                client.setScreen(new DeskScreen(false));
            }
            tickWand(client);

            director.tickBroadcast();
            director.tickAutoDirector();
        });
    }

    /**
     * Atajo opcional: si llevas en la mano el item que hayas configurado, la mesa
     * se abre sola, y se cierra al guardarlo. Comodo, pero no imprescindible: la
     * tecla funciona siempre, tambien de espectador y sin inventario.
     */
    private static void tickWand(MinecraftClient client) {
        String wand = CameraDirector.get().settings().wandItem;
        if (wand == null || wand.isBlank() || client.player == null) {
            return;
        }
        boolean holding = Registries.ITEM.getId(client.player.getMainHandStack().getItem())
                .toString().equals(wand);
        boolean deskOpen = client.currentScreen instanceof DeskScreen;
        if (holding && client.currentScreen == null) {
            client.setScreen(new DeskScreen(true));
        } else if (!holding && deskOpen && ((DeskScreen) client.currentScreen).openedByWand()) {
            client.setScreen(null);
        }
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
        camera.zoom = Math.clamp(camera.zoom() + step, 1.0f, 10.0f);
        director.touch();
        message(String.format("Zoom x%.2f", camera.zoom()), Formatting.AQUA);
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
