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
                // La escala 4 del entorno de pruebas no se parece a la de un
                // equipo real: se fuerza a 2 para ver la mesa como se vera.
                client.options.getGuiScale().setValue(2);
                client.onResolutionChanged();
                CameraDirector.get().selfTestPreviews = true;
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
            } else if (ticks == 235) {
                if ("color".equals(System.getenv("TVCAM_PREVIEW_TEST"))) {
                    CameraDirector.get().selfTestPaintPreviews();
                    TVCam.LOGGER.info("[selftest] monitores pintados de verde");
                } else {
                    CameraDirector.get().selfTestFillPreviews();
                    TVCam.LOGGER.info("[selftest] monitores rellenados con el frame actual");
                }
            } else if (ticks == 245) {
                // Se vuelca un monitor a la ventana y se captura: asi se ve lo que
                // lleva dentro de verdad, en vez de fiarse de que la copia funciona.
                CameraDirector.get().setSelfTestPresenting(false);
                if (CameraDirector.get().selfTestShowPreview(0)) {
                    CameraDirector.get().window().requestCapture(
                            java.nio.file.Path.of("tvcam-monitor.png"));
                    CameraDirector.get().selfTestShowPreview(0);
                    TVCam.LOGGER.info("[selftest] contenido del monitor 1 volcado");
                }
            } else if (ticks == 250) {
                int conImagen = 0;
                for (int i = 0; i < CameraDirector.get().cameras().size(); i++) {
                    if (CameraDirector.get().previews().hasImage(i)) {
                        conImagen++;
                    }
                }
                TVCam.LOGGER.info("[selftest] monitores con imagen: {} de {}", conImagen,
                        CameraDirector.get().cameras().size());
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
