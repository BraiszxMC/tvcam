package com.braiszx.tvcam.camera;

import com.braiszx.tvcam.TVCam;
import com.braiszx.tvcam.broadcast.BroadcastDirector;
import com.braiszx.tvcam.render.CameraWindow;
import com.braiszx.tvcam.render.FrameMirror;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * El realizador: que camaras hay, cual esta al aire, a quien enfocan, como se
 * pasa de una a otra y que frames se dibujan desde la camara en vez de desde los
 * ojos del jugador.
 *
 * <p>El juego dibuja un solo frame por vuelta en un unico framebuffer, asi que
 * para tener dos imagenes distintas hay que elegir: renderizar el mundo dos veces
 * (y perder la mitad de los FPS) o repartir los frames. TVCam reparte: 1 de cada
 * N frames se dibuja desde la camara y se presenta en la ventana de camara, el
 * resto se dibujan normal y van a la ventana del juego.
 */
public final class CameraDirector {
    private static final CameraDirector INSTANCE = new CameraDirector();

    public static CameraDirector get() {
        return INSTANCE;
    }

    private final CameraStore store = new CameraStore();
    private final CameraWindow window = new CameraWindow();
    private final FrameMirror mirror = new FrameMirror();
    private final TargetTracker target = new TargetTracker();
    private final BroadcastDirector broadcast = new BroadcastDirector();

    private int active = -1;
    private boolean cameraFrame;
    private long frameCounter;

    /** Pose y zoom con los que se esta dibujando la emision ahora mismo. */
    private CameraPose current;
    private float currentZoom = 1.0f;
    private boolean followInitialized;
    private long lastPoseNanos;
    /** Distancia de la camara al objetivo en este frame, o -1 si no hay objetivo. */
    private double aimDistance = -1.0;
    private float autoZoomFactor = 1.0f;

    /** Travelling en curso al cambiar de camara. */
    private CameraPose transitionFrom;
    private float transitionFromZoom = 1.0f;
    private long transitionStartNanos;
    private long transitionEndNanos;

    private long lastCutNanos;

    private CameraDirector() {
    }

    public TVCamSettings settings() {
        return store.settings();
    }

    public void saveSettings() {
        store.save();
    }

    public TargetTracker target() {
        return target;
    }

    public CameraWindow window() {
        return window;
    }

    public BroadcastDirector broadcast() {
        return broadcast;
    }

    // ---------------------------------------------------------------- camaras

    private String worldKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        String place;
        if (client.getCurrentServerEntry() != null) {
            place = client.getCurrentServerEntry().address;
        } else if (client.getServer() != null) {
            place = "singleplayer/" + client.getServer().getSaveProperties().getLevelName();
        } else {
            place = "desconocido";
        }
        String dimension = client.world != null ? client.world.getRegistryKey().getValue().toString() : "?";
        return place + "#" + dimension;
    }

    public List<CameraPoint> cameras() {
        return store.get(worldKey());
    }

    // ----------------------------------------------------------------- campos

    public List<Field> fields() {
        return store.fields(worldKey());
    }

    /** El campo en el que se esta trabajando, o null si no hay ninguno marcado. */
    public Field activeField() {
        String name = store.activeFieldName(worldKey());
        if (name == null) {
            return null;
        }
        for (Field field : fields()) {
            if (field.name().equalsIgnoreCase(name)) {
                return field;
            }
        }
        return null;
    }

    public boolean useField(String name) {
        if (name == null) {
            store.setActiveFieldName(worldKey(), null);
            store.save();
            return true;
        }
        for (Field field : fields()) {
            if (field.name().equalsIgnoreCase(name)) {
                store.setActiveFieldName(worldKey(), field.name());
                store.save();
                return true;
            }
        }
        return false;
    }

    public Field addField(String name, double radius) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        Vec3d pos = client.player.getEntityPos();
        Field field = new Field(name, pos.x, pos.y, pos.z, radius, null, null);
        fields().removeIf(existing -> existing.name().equalsIgnoreCase(name));
        fields().add(field);
        store.setActiveFieldName(worldKey(), name);
        store.save();
        return field;
    }

    public boolean removeField(String name) {
        boolean removed = fields().removeIf(field -> field.name().equalsIgnoreCase(name));
        if (removed) {
            String active = store.activeFieldName(worldKey());
            if (active != null && active.equalsIgnoreCase(name)) {
                store.setActiveFieldName(worldKey(), null);
            }
            store.save();
        }
        return removed;
    }

    /** Marca una de las dos porterias del campo activo donde esta el jugador. */
    public Field setGoal(int which, double radius) {
        MinecraftClient client = MinecraftClient.getInstance();
        Field field = activeField();
        if (field == null || client.player == null) {
            return null;
        }
        Vec3d pos = client.player.getEntityPos();
        Field updated = field.withGoal(which, new Field.Goal(pos.x, pos.y, pos.z, radius));
        List<Field> list = fields();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(field.name())) {
                list.set(i, updated);
                break;
            }
        }
        store.save();
        return updated;
    }

    public CameraPoint addHere(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        List<CameraPoint> list = cameras();
        String finalName = (name == null || name.isBlank()) ? "Camara " + (list.size() + 1) : name;
        CameraPoint point = CameraPoint.fixed(finalName,
                new Vec3d(client.player.getX(), client.player.getEyeY(), client.player.getZ()),
                client.player.getYaw(), client.player.getPitch());
        list.add(point);
        store.save();
        return point;
    }

    public boolean remove(int index) {
        List<CameraPoint> list = cameras();
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        if (active >= list.size()) {
            active = list.isEmpty() ? -1 : list.size() - 1;
        }
        store.save();
        return true;
    }

    public void clear() {
        cameras().clear();
        active = -1;
        store.save();
    }

    public CameraPoint activeCamera() {
        List<CameraPoint> list = cameras();
        return (active >= 0 && active < list.size()) ? list.get(active) : null;
    }

    public int activeIndex() {
        return active;
    }

    public boolean replace(int index, CameraPoint camera) {
        List<CameraPoint> list = cameras();
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.set(index, camera);
        store.save();
        return true;
    }

    // ------------------------------------------------------------------ cortes

    /** Corta a la camara indicada (0-based). -1 apaga la emision. */
    public void cut(int index) {
        cut(index, false);
    }

    private void cut(int index, boolean automatic) {
        List<CameraPoint> list = cameras();
        if (index < 0) {
            active = -1;
            feedback(Text.literal("TVCam: emision parada").formatted(Formatting.GRAY));
            return;
        }
        if (index >= list.size()) {
            feedback(Text.literal("TVCam: no hay camara " + (index + 1)).formatted(Formatting.RED));
            return;
        }
        startTransition();
        active = index;
        lastCutNanos = System.nanoTime();
        CameraPoint point = list.get(index);
        if (!automatic) {
            feedback(Text.literal("TVCam: al aire " + (index + 1) + " - " + point.name())
                    .formatted(Formatting.AQUA));
        }
        if (!window.isOpen()) {
            window.open();
        }
    }

    /** Congela la pose actual como origen del travelling hacia la camara nueva. */
    private void startTransition() {
        int millis = settings().transitionMillis;
        followInitialized = false;
        if (millis <= 0 || current == null || active < 0) {
            transitionEndNanos = 0L;
            return;
        }
        transitionFrom = current;
        transitionFromZoom = currentZoom;
        transitionStartNanos = System.nanoTime();
        transitionEndNanos = transitionStartNanos + millis * 1_000_000L;
    }

    private void feedback(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(text, true);
        }
    }

    // ------------------------------------------------------------------ poses

    /**
     * Donde y hacia donde mira la emision en este frame. Aqui es donde el modo de
     * la camara, el seguimiento del objetivo y el travelling se convierten en una
     * sola pose.
     */
    public CameraPose poseFor(float tickDelta) {
        CameraPoint camera = activeCamera();
        if (camera == null) {
            return null;
        }
        long now = System.nanoTime();
        double deltaSeconds = lastPoseNanos == 0L ? 0.05 : (now - lastPoseNanos) / 1_000_000_000.0;
        lastPoseNanos = now;
        deltaSeconds = Math.clamp(deltaSeconds, 0.0, 0.5);

        CameraPose desired = desiredPose(camera, tickDelta, deltaSeconds);
        float zoom = camera.zoom() * autoZoom(deltaSeconds);

        if (now < transitionEndNanos && transitionFrom != null) {
            float progress = (float) (now - transitionStartNanos)
                    / (float) (transitionEndNanos - transitionStartNanos);
            float eased = ease(MathHelper.clamp(progress, 0.0f, 1.0f));
            current = CameraPose.lerp(transitionFrom, desired, eased);
            currentZoom = MathHelper.lerp(eased, transitionFromZoom, zoom);
        } else {
            current = desired;
            currentZoom = zoom;
        }
        return current;
    }

    /** Suavizado de entrada y salida, para que el travelling no arranque de golpe. */
    private static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private CameraPose desiredPose(CameraPoint camera, float tickDelta, double deltaSeconds) {
        CameraPose fixed = new CameraPose(camera.pos(), camera.yaw(), camera.pitch());
        Entity entity = target.resolve();
        if (entity == null) {
            aimDistance = -1.0;
            // Sin objetivo, la camara se queda quieta donde la pusiste en vez de
            // ponerse a mirar al vacio.
            return fixed;
        }
        Vec3d aim = target.aimPoint(entity, tickDelta, settings().aimOffset);
        Vec3d eye = camera.mode() == CameraMode.ACOMPANAR ? aim.add(camera.offset()) : camera.pos();
        // Se mide aunque la camara sea fija: el zoom automatico tambien le sirve.
        aimDistance = eye.distanceTo(aim);
        if (!camera.mode().needsTarget()) {
            return fixed;
        }
        CameraPose wanted = CameraPose.lookAt(eye, aim);

        if (!followInitialized) {
            followInitialized = true;
            return wanted;
        }

        double response = settings().followResponse();
        if (response == Double.MAX_VALUE) {
            return wanted;
        }
        // Acercamiento exponencial: independiente de los FPS y sin rebote.
        float blend = (float) (1.0 - Math.exp(-response * deltaSeconds));
        CameraPose previous = current != null ? current : wanted;
        return new CameraPose(
                previous.pos().lerp(wanted.pos(), blend),
                CameraPose.lerpAngle(previous.yaw(), wanted.yaw(), blend),
                MathHelper.lerp(blend, previous.pitch(), wanted.pitch()));
    }

    /**
     * Zoom automatico: aprieta cuando la jugada se aleja y se abre cuando se
     * acerca, para que el objetivo se vea siempre parecido de grande. Se mueve
     * despacio y con zona muerta, porque un zoom que persigue cada centimetro
     * marea y no es lo que hace un camara de verdad.
     */
    private float autoZoom(double deltaSeconds) {
        TVCamSettings config = settings();
        if (!config.autoZoom || aimDistance < 0.0) {
            autoZoomFactor = MathHelper.lerp(
                    (float) (1.0 - Math.exp(-config.zoomResponse() * deltaSeconds)), autoZoomFactor, 1.0f);
            return autoZoomFactor;
        }
        float wanted = (float) Math.clamp(aimDistance / config.autoZoomDistance, 1.0, config.autoZoomMax);
        if (Math.abs(wanted - autoZoomFactor) > 0.02f) {
            float blend = (float) (1.0 - Math.exp(-config.zoomResponse() * deltaSeconds));
            autoZoomFactor = MathHelper.lerp(blend, autoZoomFactor, wanted);
        }
        return autoZoomFactor;
    }

    public float currentZoom() {
        return currentZoom;
    }

    /** Cuanto esta apretando el zoom automatico ahora mismo. */
    public float autoZoomFactor() {
        return autoZoomFactor;
    }

    /** Distancia de la camara al objetivo, o -1 si no hay objetivo. */
    public double aimDistance() {
        return aimDistance;
    }

    // ------------------------------------------------------- realizador automatico

    /** Llamado una vez por tick. */
    public void tickBroadcast() {
        broadcast.tick(target.kind() == TargetTracker.Kind.NINGUNO ? null : target.resolve(), activeField());
    }

    /** Llamado una vez por tick: decide si toca cortar a otra camara. */
    public void tickAutoDirector() {
        TVCamSettings config = settings();
        if (!config.autoDirector || active < 0 || !window.isOpen()) {
            return;
        }
        Entity entity = target.resolve();
        if (entity == null) {
            return;
        }
        long minimum = (long) (config.autoMinShotSeconds * 1_000_000_000L);
        if (System.nanoTime() - lastCutNanos < minimum) {
            return;
        }
        int best = pickBestCamera(entity.getBoundingBox().getCenter());
        if (best >= 0 && best != active) {
            cut(best, true);
        }
    }

    /**
     * Elige el plano que mejor cuenta la jugada: ni pegado ni en la otra punta del
     * campo, y con el objetivo dentro del encuadre si la camara no puede girar.
     */
    private int pickBestCamera(Vec3d targetPos) {
        List<CameraPoint> list = cameras();
        int best = -1;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            CameraPoint camera = list.get(i);
            Vec3d eye = camera.mode() == CameraMode.ACOMPANAR
                    ? targetPos.add(camera.offset()) : camera.pos();
            double distance = eye.distanceTo(targetPos);
            if (distance < 3.0 || distance > 120.0) {
                continue;
            }
            double score = -Math.abs(distance - 22.0);
            if (!camera.mode().needsTarget()) {
                // Una camara que no gira solo sirve si la jugada le entra en el plano.
                double off = angleFromView(camera, targetPos);
                if (off > 55.0) {
                    continue;
                }
                score -= off * 0.35;
            }
            if (i == active) {
                // Un pelin de inercia, para no cortar de ida y vuelta entre dos planos
                // casi igual de buenos.
                score += 4.0;
            }
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static double angleFromView(CameraPoint camera, Vec3d targetPos) {
        CameraPose wanted = CameraPose.lookAt(camera.pos(), targetPos);
        double yawOff = Math.abs(MathHelper.wrapDegrees(wanted.yaw() - camera.yaw()));
        double pitchOff = Math.abs(MathHelper.wrapDegrees(wanted.pitch() - camera.pitch()));
        return Math.max(yawOff, pitchOff);
    }

    // ---------------------------------------------------------------- ventana

    public void toggleWindow() {
        if (window.isOpen()) {
            window.close();
        } else {
            window.open();
        }
    }

    // ----------------------------------------------------------------- frames

    public boolean isCameraFrame() {
        return cameraFrame;
    }

    private boolean canBroadcast() {
        MinecraftClient client = MinecraftClient.getInstance();
        return window.isOpen()
                && activeCamera() != null
                && client.world != null
                && client.player != null
                && client.currentScreen == null;
    }

    public void beginFrame() {
        frameCounter++;
        cameraFrame = canBroadcast() && mirror.hasFrame()
                && (frameCounter % settings().frameRatio == 0L);
    }

    public void endFrame() {
        cameraFrame = false;
    }

    /**
     * Justo antes de que el juego presente el frame en tu ventana: si era un frame
     * de camara lo mandamos a la ventana de camara y devolvemos el tuyo a la tuya,
     * para que no parpadee entre las dos vistas.
     */
    public void beforePresent() {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) {
            return;
        }
        try {
            if (selfTestPresenting) {
                if (window.present(framebuffer)) {
                    selfTestFrames++;
                }
                return;
            }
            if (cameraFrame) {
                window.present(framebuffer);
                mirror.present();
            } else {
                mirror.capture(framebuffer);
            }
        } catch (RuntimeException e) {
            TVCam.LOGGER.error("Fallo presentando el frame de camara, cierro la ventana", e);
            window.close();
            mirror.close();
            active = -1;
        }
    }

    // ------------------------------------------------------------- autoprueba

    private boolean selfTestPresenting;
    private int selfTestFrames;

    public void setSelfTestPresenting(boolean value) {
        selfTestPresenting = value;
    }

    public int selfTestPresentedFrames() {
        return selfTestFrames;
    }

    /** Comprueba que la copia del frame (el espejo) funciona contra esta version. */
    public String selfTestMirror() {
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (framebuffer == null) {
            return "sin framebuffer";
        }
        try {
            mirror.capture(framebuffer);
            boolean presented = mirror.present();
            return "capturado=" + mirror.hasFrame() + " presentado=" + presented;
        } catch (RuntimeException e) {
            TVCam.LOGGER.error("[selftest] el espejo fallo", e);
            return "FALLO: " + e;
        }
    }

    public void shutdown() {
        window.close();
        mirror.close();
    }
}
