package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraMode;
import com.braiszx.tvcam.camera.CameraPoint;
import com.braiszx.tvcam.camera.TargetSpec;
import com.braiszx.tvcam.gui.DeskScreen;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Autoprueba de la segunda ventana, para comprobar en un arranque sin tocar nada
 * que GLFW crea la ventana, que comparte el contexto de OpenGL con el juego y que
 * la textura del frame se pinta en ella.
 *
 * <p>Solo se activa con la variable de entorno {@code TVCAM_SELFTEST=1}.
 */
public final class SelfTest {
    private static int ticks;

    private SelfTest() {
    }

    private static CameraPoint demo(String name, CameraMode mode, TargetSpec target, float zoom) {
        CameraPoint camera = CameraPoint.at(name, new Vec3d(0, 70, 0), 0.0f, 0.0f);
        camera.mode = mode;
        camera.target = target;
        camera.zoom = zoom;
        return camera;
    }

    public static void register() {
        TVCam.LOGGER.info("[selftest] activada");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            if (ticks == 200) {
                // Unas camaras de mentira para poder ver la mesa montada.
                if (CameraDirector.get().cameras().isEmpty()) {
                    CameraDirector.get().cameras().add(demo("Banda", CameraMode.SEGUIR, TargetSpec.ball(), 1.0f));
                    CameraDirector.get().cameras().add(demo("Porteria norte", CameraMode.SEGUIR, TargetSpec.ball(), 2.5f));
                    CameraDirector.get().cameras().add(demo("Grada", CameraMode.FIJA, TargetSpec.none(), 1.0f));
                    CameraDirector.get().cameras().add(demo("Sigue a Braiszx", CameraMode.ACOMPANAR, TargetSpec.player("Braiszx"), 1.0f));
                }
                client.setScreen(new DeskScreen(false));
                TVCam.LOGGER.info("[selftest] mesa de realizacion abierta");
            } else if (ticks == 215) {
                CameraDirector.get().window().open();
                TVCam.LOGGER.info("[selftest] ventana abierta: {}",
                        CameraDirector.get().window().isOpen());
                CameraDirector.get().setSelfTestPresenting(true);
            } else if (ticks == 240) {
                CameraDirector.get().window().requestCapture(
                        java.nio.file.Path.of(System.getProperty("tvcam.selftest.capture",
                                "tvcam-selftest.png")));
            } else if (ticks == 255) {
                TVCam.LOGGER.info("[selftest] espejo del frame: {}",
                        CameraDirector.get().selfTestMirror());
            } else if (ticks == 270) {
                TVCam.LOGGER.info("[selftest] frames presentados en la ventana: {}",
                        CameraDirector.get().selfTestPresentedFrames());
                CameraDirector.get().setSelfTestPresenting(false);
                CameraDirector.get().shutdown();
                TVCam.LOGGER.info("[selftest] terminada, cierro el juego");
                MinecraftClient.getInstance().scheduleStop();
            }
        });
    }
}
