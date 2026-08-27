package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraMode;
import com.braiszx.tvcam.camera.CameraPoint;
import com.braiszx.tvcam.camera.TVCamSettings;
import com.braiszx.tvcam.camera.TargetTracker;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Comandos de cliente: no los ve el servidor, asi que funcionan en cualquier
 * servidor sin que tenga el mod instalado.
 */
public final class Commands {
    private Commands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("tvcam")
                        .then(literal("add")
                                .executes(c -> add(c.getSource(), null))
                                .then(argument("nombre", StringArgumentType.greedyString())
                                        .executes(c -> add(c.getSource(),
                                                StringArgumentType.getString(c, "nombre")))))
                        .then(literal("list").executes(c -> list(c.getSource())))
                        .then(literal("del").then(argument("numero", IntegerArgumentType.integer(1))
                                .executes(c -> delete(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "numero")))))
                        .then(literal("clear").executes(c -> clear(c.getSource())))
                        .then(literal("cut").then(argument("numero", IntegerArgumentType.integer(0))
                                .executes(c -> cut(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "numero")))))
                        .then(literal("window").executes(c -> toggleWindow(c.getSource())))

                        .then(literal("mode")
                                .then(argument("numero", IntegerArgumentType.integer(1))
                                        .then(argument("modo", StringArgumentType.word())
                                                .executes(c -> mode(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "numero"),
                                                        StringArgumentType.getString(c, "modo"))))))
                        .then(literal("zoom").then(argument("factor", DoubleArgumentType.doubleArg(1.0, 10.0))
                                .executes(c -> zoom(c.getSource(),
                                        (float) DoubleArgumentType.getDouble(c, "factor")))))

                        .then(literal("ball").executes(c -> followBall(c.getSource())))
                        .then(literal("lock").executes(c -> lockOn(c.getSource())))
                        .then(literal("player").then(argument("nombre", StringArgumentType.word())
                                .executes(c -> followPlayer(c.getSource(),
                                        StringArgumentType.getString(c, "nombre")))))
                        .then(literal("notarget").executes(c -> noTarget(c.getSource())))

                        .then(literal("res")
                                .then(argument("ancho", IntegerArgumentType.integer(320, 7680))
                                        .then(argument("alto", IntegerArgumentType.integer(180, 4320))
                                                .executes(c -> resolution(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "ancho"),
                                                        IntegerArgumentType.getInteger(c, "alto"))))))
                        .then(literal("transition").then(argument("ms", IntegerArgumentType.integer(0, 10000))
                                .executes(c -> transition(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "ms")))))
                        .then(literal("smooth").then(argument("valor", IntegerArgumentType.integer(0, 100))
                                .executes(c -> smooth(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "valor")))))
                        .then(literal("ratio").then(argument("n", IntegerArgumentType.integer(2, 10))
                                .executes(c -> ratio(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "n")))))
                        .then(literal("aimoffset").then(argument("bloques", DoubleArgumentType.doubleArg(-5, 5))
                                .executes(c -> aimOffset(c.getSource(),
                                        DoubleArgumentType.getDouble(c, "bloques")))))
                        .then(literal("autozoom")
                                .then(argument("activo", BoolArgumentType.bool())
                                        .executes(c -> autoZoom(c.getSource(),
                                                BoolArgumentType.getBool(c, "activo"))))
                                .then(literal("dist").then(argument("bloques", DoubleArgumentType.doubleArg(4, 200))
                                        .executes(c -> autoZoomDistance(c.getSource(),
                                                DoubleArgumentType.getDouble(c, "bloques")))))
                                .then(literal("max").then(argument("factor", DoubleArgumentType.doubleArg(1, 10))
                                        .executes(c -> autoZoomMax(c.getSource(),
                                                (float) DoubleArgumentType.getDouble(c, "factor")))))
                                .then(literal("speed").then(argument("valor", IntegerArgumentType.integer(0, 100))
                                        .executes(c -> autoZoomSpeed(c.getSource(),
                                                IntegerArgumentType.getInteger(c, "valor"))))))
                        .then(literal("auto").then(argument("activo", BoolArgumentType.bool())
                                .executes(c -> auto(c.getSource(), BoolArgumentType.getBool(c, "activo")))))
                        .then(literal("info").executes(c -> info(c.getSource())))
                        .executes(c -> help(c.getSource()))));
    }

    // ---------------------------------------------------------------- camaras

    private static int add(FabricClientCommandSource source, String name) {
        CameraPoint point = CameraDirector.get().addHere(name);
        if (point == null) {
            source.sendError(Text.literal("No se pudo crear la camara"));
            return 0;
        }
        source.sendFeedback(Text.literal("Camara " + CameraDirector.get().cameras().size()
                + " creada: " + point.name()).formatted(Formatting.GREEN));
        return 1;
    }

    private static int list(FabricClientCommandSource source) {
        List<CameraPoint> cameras = CameraDirector.get().cameras();
        if (cameras.isEmpty()) {
            source.sendFeedback(Text.literal("No hay camaras en este mundo. Usa /tvcam add")
                    .formatted(Formatting.GRAY));
            return 1;
        }
        source.sendFeedback(Text.literal("Camaras de este mundo:").formatted(Formatting.AQUA));
        for (int i = 0; i < cameras.size(); i++) {
            CameraPoint camera = cameras.get(i);
            boolean live = i == CameraDirector.get().activeIndex();
            source.sendFeedback(Text.literal(String.format("  %d. %s  [%s]  x%.1f  (%.0f, %.0f, %.0f)%s",
                            i + 1, camera.name(), camera.mode().name().toLowerCase(), camera.zoom(),
                            camera.x(), camera.y(), camera.z(), live ? "  <- al aire" : ""))
                    .formatted(live ? Formatting.GREEN : Formatting.WHITE));
        }
        return 1;
    }

    private static int delete(FabricClientCommandSource source, int number) {
        if (CameraDirector.get().remove(number - 1)) {
            source.sendFeedback(Text.literal("Camara " + number + " borrada").formatted(Formatting.YELLOW));
            return 1;
        }
        source.sendError(Text.literal("No existe la camara " + number));
        return 0;
    }

    private static int clear(FabricClientCommandSource source) {
        CameraDirector.get().clear();
        source.sendFeedback(Text.literal("Todas las camaras borradas").formatted(Formatting.YELLOW));
        return 1;
    }

    private static int cut(FabricClientCommandSource source, int number) {
        CameraDirector.get().cut(number - 1);
        return 1;
    }

    private static int toggleWindow(FabricClientCommandSource source) {
        CameraDirector.get().toggleWindow();
        source.sendFeedback(Text.literal(CameraDirector.get().window().isOpen()
                ? "Ventana de camara abierta" : "Ventana de camara cerrada").formatted(Formatting.AQUA));
        return 1;
    }

    private static int mode(FabricClientCommandSource source, int number, String modeName) {
        CameraMode mode = CameraMode.parse(modeName);
        if (mode == null) {
            source.sendError(Text.literal("Modos: fija, seguir, acompanar"));
            return 0;
        }
        CameraDirector director = CameraDirector.get();
        List<CameraPoint> cameras = director.cameras();
        int index = number - 1;
        if (index < 0 || index >= cameras.size()) {
            source.sendError(Text.literal("No existe la camara " + number));
            return 0;
        }
        CameraPoint camera = cameras.get(index);
        Vec3d offset = Vec3d.ZERO;
        if (mode == CameraMode.ACOMPANAR) {
            Entity target = director.target().resolve();
            if (target == null) {
                source.sendError(Text.literal(
                        "Para el modo acompanar hace falta un objetivo: usa /tvcam ball o /tvcam lock"));
                return 0;
            }
            // La distancia que hay ahora entre camara y objetivo es la que mantendra.
            offset = camera.pos().subtract(target.getBoundingBox().getCenter());
        }
        director.replace(index, camera.withMode(mode, offset));
        source.sendFeedback(Text.literal("Camara " + number + " en modo " + mode.name().toLowerCase())
                .formatted(Formatting.GREEN));
        return 1;
    }

    private static int zoom(FabricClientCommandSource source, float factor) {
        CameraDirector director = CameraDirector.get();
        int index = director.activeIndex();
        CameraPoint camera = director.activeCamera();
        if (camera == null) {
            source.sendError(Text.literal("No hay ninguna camara al aire"));
            return 0;
        }
        director.replace(index, camera.withZoom(factor));
        source.sendFeedback(Text.literal(String.format("Zoom x%.1f en la camara %d", factor, index + 1))
                .formatted(Formatting.AQUA));
        return 1;
    }

    // -------------------------------------------------------------- objetivos

    private static int followBall(FabricClientCommandSource source) {
        CameraDirector.get().target().followBall();
        source.sendFeedback(Text.literal(
                        "Objetivo: la pelota. Las camaras en modo seguir o acompanar la enfocaran.")
                .formatted(Formatting.GREEN));
        return 1;
    }

    private static int lockOn(FabricClientCommandSource source) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = TargetTracker.entityUnderCrosshair(client);
        if (entity == null || !CameraDirector.get().target().followEntity(entity)) {
            source.sendError(Text.literal("Apunta a algo primero (hasta 160 bloques)"));
            return 0;
        }
        source.sendFeedback(Text.literal("Objetivo marcado: " + entity.getName().getString())
                .formatted(Formatting.GREEN));
        return 1;
    }

    private static int followPlayer(FabricClientCommandSource source, String name) {
        CameraDirector.get().target().followPlayer(name);
        source.sendFeedback(Text.literal("Objetivo: el jugador " + name).formatted(Formatting.GREEN));
        return 1;
    }

    private static int noTarget(FabricClientCommandSource source) {
        CameraDirector.get().target().clear();
        source.sendFeedback(Text.literal("Sin objetivo").formatted(Formatting.GRAY));
        return 1;
    }

    // --------------------------------------------------------------- ajustes

    private static int resolution(FabricClientCommandSource source, int width, int height) {
        CameraDirector director = CameraDirector.get();
        TVCamSettings settings = director.settings();
        settings.windowWidth = width;
        settings.windowHeight = height;
        settings.normalized();
        director.saveSettings();
        director.window().resize(settings.windowWidth, settings.windowHeight);
        source.sendFeedback(Text.literal("Ventana de camara a " + width + "x" + height)
                .formatted(Formatting.AQUA));
        int[] main = mainWindowSize();
        if (main != null && (width > main[0] || height > main[1])) {
            source.sendFeedback(Text.literal("Aviso: tu ventana de Minecraft es " + main[0] + "x" + main[1]
                            + ", asi que la emision se escala hacia arriba desde ahi y no gana detalle.")
                    .formatted(Formatting.YELLOW));
        }
        return 1;
    }

    private static int[] mainWindowSize() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) {
            return null;
        }
        return new int[] {client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight()};
    }

    private static int transition(FabricClientCommandSource source, int millis) {
        CameraDirector.get().settings().transitionMillis = millis;
        CameraDirector.get().saveSettings();
        source.sendFeedback(Text.literal(millis == 0
                ? "Cortes secos entre camaras"
                : "Travelling de " + millis + " ms al cambiar de camara").formatted(Formatting.AQUA));
        return 1;
    }

    private static int smooth(FabricClientCommandSource source, int value) {
        CameraDirector.get().settings().smoothing = value;
        CameraDirector.get().saveSettings();
        source.sendFeedback(Text.literal("Suavizado del seguimiento: " + value + "/100")
                .formatted(Formatting.AQUA));
        return 1;
    }

    private static int ratio(FabricClientCommandSource source, int n) {
        CameraDirector.get().settings().frameRatio = n;
        CameraDirector.get().saveSettings();
        source.sendFeedback(Text.literal("1 de cada " + n + " frames va a la emision")
                .formatted(Formatting.AQUA));
        return 1;
    }

    private static int aimOffset(FabricClientCommandSource source, double blocks) {
        CameraDirector.get().settings().aimOffset = blocks;
        CameraDirector.get().saveSettings();
        source.sendFeedback(Text.literal(String.format("Encuadre %.2f bloques mas arriba", blocks))
                .formatted(Formatting.AQUA));
        return 1;
    }

    private static int auto(FabricClientCommandSource source, boolean enabled) {
        CameraDirector.get().settings().autoDirector = enabled;
        CameraDirector.get().saveSettings();
        source.sendFeedback(Text.literal(enabled
                        ? "Realizador automatico activado: corta solo a la camara que mejor ve la jugada"
                        : "Realizador automatico desactivado")
                .formatted(enabled ? Formatting.GREEN : Formatting.GRAY));
        return 1;
    }

    private static int autoZoom(FabricClientCommandSource source, boolean enabled) {
        CameraDirector director = CameraDirector.get();
        director.settings().autoZoom = enabled;
        director.saveSettings();
        if (enabled && director.target().kind() == TargetTracker.Kind.NINGUNO) {
            source.sendFeedback(Text.literal(
                            "Zoom automatico activado, pero no hay objetivo: usa /tvcam ball o /tvcam lock")
                    .formatted(Formatting.YELLOW));
            return 1;
        }
        source.sendFeedback(Text.literal(enabled
                        ? String.format("Zoom automatico activado (x1 a %.0f bloques, tope x%.1f)",
                        director.settings().autoZoomDistance, director.settings().autoZoomMax)
                        : "Zoom automatico desactivado")
                .formatted(enabled ? Formatting.GREEN : Formatting.GRAY));
        return 1;
    }

    private static int autoZoomDistance(FabricClientCommandSource source, double blocks) {
        CameraDirector.get().settings().autoZoomDistance = blocks;
        CameraDirector.get().settings().normalized();
        CameraDirector.get().saveSettings();
        source.sendFeedback(Text.literal(String.format(
                        "El zoom automatico se queda en x1 a %.0f bloques; mas lejos, aprieta", blocks))
                .formatted(Formatting.AQUA));
        return 1;
    }

    private static int autoZoomMax(FabricClientCommandSource source, float factor) {
        CameraDirector.get().settings().autoZoomMax = factor;
        CameraDirector.get().settings().normalized();
        CameraDirector.get().saveSettings();
        source.sendFeedback(Text.literal(String.format("Tope del zoom automatico: x%.1f", factor))
                .formatted(Formatting.AQUA));
        return 1;
    }

    private static int autoZoomSpeed(FabricClientCommandSource source, int value) {
        CameraDirector.get().settings().autoZoomSpeed = value;
        CameraDirector.get().saveSettings();
        source.sendFeedback(Text.literal("Velocidad del zoom automatico: " + value + "/100")
                .formatted(Formatting.AQUA));
        return 1;
    }

    private static int info(FabricClientCommandSource source) {
        CameraDirector director = CameraDirector.get();
        TVCamSettings settings = director.settings();
        int[] main = mainWindowSize();
        int[] camera = director.window().size();
        source.sendFeedback(Text.literal("TVCam").formatted(Formatting.AQUA, Formatting.BOLD));
        source.sendFeedback(Text.literal(
                "  origen (tu ventana): " + (main == null ? "?" : main[0] + "x" + main[1])
                        + "\n  ventana de camara: " + (camera == null ? "cerrada" : camera[0] + "x" + camera[1])
                        + "\n  reparto de frames: 1 de cada " + settings.frameRatio
                        + "\n  travelling: " + settings.transitionMillis + " ms"
                        + "\n  suavizado: " + settings.smoothing + "/100"
                        + "\n  objetivo: " + director.target().describe()
                        + "\n  zoom automatico: " + (settings.autoZoom
                        ? String.format("si (x1 a %.0f bloques, tope x%.1f, ahora x%.2f)",
                        settings.autoZoomDistance, settings.autoZoomMax, director.autoZoomFactor())
                        : "no")
                        + "\n  realizador automatico: " + (settings.autoDirector ? "si" : "no"))
                .formatted(Formatting.GRAY));
        return 1;
    }

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("TVCam").formatted(Formatting.AQUA, Formatting.BOLD));
        source.sendFeedback(Text.literal("""
                CAMARAS
                  /tvcam add [nombre]      crea una camara donde estas mirando
                  /tvcam list              lista las camaras del mundo
                  /tvcam del <n>           borra una camara
                  /tvcam cut <n>           corta a esa camara (0 = parar)
                  /tvcam mode <n> <modo>   fija | seguir | acompanar
                  /tvcam zoom <1-10>       zoom de la camara al aire
                OBJETIVO
                  /tvcam ball              seguir la pelota de BlockBall
                  /tvcam lock              seguir aquello a lo que apuntas
                  /tvcam player <nombre>   seguir a un jugador
                EMISION
                  /tvcam res <ancho> <alto>  tamano de la ventana (lo que captura OBS)
                  /tvcam transition <ms>     duracion del travelling al cortar
                  /tvcam smooth <0-100>      pulso del camara al seguir
                  /tvcam ratio <2-10>        1 de cada N frames va a la emision
                  /tvcam autozoom <true|false>  aprieta el zoom si la jugada se aleja
                  /tvcam autozoom dist <n>      distancia a la que se queda en x1
                  /tvcam autozoom max <n>       tope del zoom automatico
                  /tvcam autozoom speed <0-100> lo rapido que se mueve
                  /tvcam auto <true|false>   realizador automatico
                  /tvcam info                resolucion y ajustes actuales
                TECLAS: Numpad 1-9 cortan, 0 para, * crea, . abre la ventana,
                + y - zoom, / marca objetivo, Intro realizador automatico""")
                .formatted(Formatting.GRAY));
        return 1;
    }
}
