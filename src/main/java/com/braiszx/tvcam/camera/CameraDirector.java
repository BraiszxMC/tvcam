package com.braiszx.tvcam.camera;

import com.braiszx.tvcam.TVCam;
import com.braiszx.tvcam.broadcast.BroadcastDirector;
import com.braiszx.tvcam.render.CameraWindow;
import com.braiszx.tvcam.render.FrameMirror;
import com.braiszx.tvcam.render.PreviewBank;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
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
    private final PreviewBank previews = new PreviewBank();

    private int active = -1;
    private boolean cameraFrame;
    private boolean hudHiddenBackup;
    /** Camara cuya miniatura se esta dibujando en este frame, o -1. */
    private int previewFrame = -1;
    /** Cuantas camaras quieren miniatura (la mesa abierta las pide). */
    private boolean previewsWanted;
    private int previewCursor;
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

    public PreviewBank previews() {
        return previews;
    }

    /** La mesa avisa de que quiere monitores; sin mesa abierta no se gasta un frame. */
    public void setPreviewsWanted(boolean wanted) {
        previewsWanted = wanted;
    }

    /** true si en este frame se esta dibujando el mundo para un monitor. */
    public int previewIndex() {
        return previewFrame;
    }

    public boolean isPreviewFrame() {
        return previewFrame >= 0;
    }

    /**
     * true si lo que se dibuja en este frame es para la emision o para un monitor:
     * en ambos casos no debe salir el HUD, ni la mano, ni ninguna pantalla.
     */
    public boolean isBroadcastFrame() {
        return cameraFrame || previewFrame >= 0;
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
        CameraPoint point = CameraPoint.at(finalName,
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

    /** Trae la camara a donde estas, con tu mismo encuadre. */
    public boolean moveHere(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        CameraPoint camera = at(index);
        if (camera == null || client.player == null) {
            return false;
        }
        camera.moveTo(new Vec3d(client.player.getX(), client.player.getEyeY(), client.player.getZ()),
                client.player.getYaw(), client.player.getPitch());
        store.save();
        return true;
    }

    /** Reapunta la camara sin moverla de sitio. */
    public boolean aimHere(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        CameraPoint camera = at(index);
        if (camera == null || client.player == null) {
            return false;
        }
        camera.aimAt(client.player.getYaw(), client.player.getPitch());
        store.save();
        return true;
    }

    public CameraPoint at(int index) {
        List<CameraPoint> list = cameras();
        return (index >= 0 && index < list.size()) ? list.get(index) : null;
    }

    public boolean duplicate(int index) {
        CameraPoint camera = at(index);
        if (camera == null) {
            return false;
        }
        CameraPoint copy = camera.copy();
        copy.name = camera.name() + " (2)";
        cameras().add(index + 1, copy);
        store.save();
        return true;
    }

    /** Sube o baja una camara en la lista, que es la que mapea las teclas 1-9. */
    public boolean move(int index, int direction) {
        List<CameraPoint> list = cameras();
        int destination = index + direction;
        if (index < 0 || index >= list.size() || destination < 0 || destination >= list.size()) {
            return false;
        }
        CameraPoint camera = list.remove(index);
        list.add(destination, camera);
        if (active == index) {
            active = destination;
        } else if (active == destination) {
            active = index;
        }
        store.save();
        return true;
    }

    /** Guarda los cambios hechos sobre una camara ya existente. */
    public void touch() {
        store.save();
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
        if (previewFrame >= 0) {
            // Los monitores van sin suavizado ni travelling: son una foto fija de
            // lo que veria esa camara ahora mismo, y no deben tocar el estado de la
            // camara que esta al aire.
            return previewPose(previewFrame, tickDelta);
        }
        CameraPoint camera = activeCamera();
        if (camera == null) {
            return null;
        }
        long now = System.nanoTime();
        double deltaSeconds = lastPoseNanos == 0L ? 0.05 : (now - lastPoseNanos) / 1_000_000_000.0;
        lastPoseNanos = now;
        deltaSeconds = Math.clamp(deltaSeconds, 0.0, 0.5);

        CameraPose desired = desiredPose(camera, tickDelta, deltaSeconds);
        float zoom = camera.zoom() * autoZoom(camera, deltaSeconds);

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

    private CameraPose previewPose(int index, float tickDelta) {
        CameraPoint camera = at(index);
        if (camera == null) {
            return null;
        }
        CameraPose fixed = new CameraPose(camera.pos(), camera.yaw(), camera.pitch());
        if (!camera.mode().needsTarget()) {
            return fixed;
        }
        Entity entity = target.resolve(camera.target());
        if (entity == null) {
            return fixed;
        }
        Vec3d aim = target.aimPoint(entity, tickDelta, settings().aimOffset);
        Vec3d eye = camera.mode() == CameraMode.ACOMPANAR ? aim.add(camera.offset()) : camera.pos();
        return CameraPose.lookAt(eye, aim);
    }

    /** El zoom con el que se dibuja el monitor de una camara. */
    public float previewZoom(int index) {
        CameraPoint camera = at(index);
        return camera == null ? 1.0f : camera.zoom();
    }

    /** Suavizado de entrada y salida, para que el travelling no arranque de golpe. */
    private static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private CameraPose desiredPose(CameraPoint camera, float tickDelta, double deltaSeconds) {
        CameraPose fixed = new CameraPose(camera.pos(), camera.yaw(), camera.pitch());
        Entity entity = target.resolve(camera.target());
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

        double response = camera.smoothing != null
                ? settings().followResponse(camera.smoothing)
                : settings().followResponse();
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
    private float autoZoom(CameraPoint camera, double deltaSeconds) {
        TVCamSettings config = settings();
        boolean enabled = camera.autoZoom != null ? camera.autoZoom : config.autoZoom;
        if (!enabled || aimDistance < 0.0) {
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
        long minimum = (long) (config.autoMinShotSeconds * 1_000_000_000L);
        if (System.nanoTime() - lastCutNanos < minimum) {
            return;
        }
        int best = pickBestCamera();
        if (best >= 0 && best != active) {
            cut(best, true);
        }
    }

    /**
     * Elige el plano que mejor cuenta la jugada: ni pegado ni en la otra punta del
     * campo, y con el objetivo dentro del encuadre si la camara no puede girar.
     */
    private int pickBestCamera() {
        List<CameraPoint> list = cameras();
        int best = -1;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            CameraPoint camera = list.get(i);
            if (camera.autoSkip) {
                // Camara reservada: solo sale al aire si la pones tu a mano.
                continue;
            }
            // Cada camara puntua con SU objetivo, no con el comun: si una sigue a
            // un jugador y otra la pelota, cada una se juzga por lo suyo.
            Entity entity = target.resolve(camera.target());
            if (entity == null) {
                continue;
            }
            Vec3d targetPos = entity.getBoundingBox().getCenter();
            Vec3d eye = camera.mode() == CameraMode.ACOMPANAR
                    ? targetPos.add(camera.offset()) : camera.pos();
            double distance = eye.distanceTo(targetPos);
            if (distance < 3.0 || distance > 120.0) {
                continue;
            }
            if (!canSee(eye, targetPos)) {
                // Sin linea de vision se cortaba a planos que solo enseñaban un muro.
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

    /** Si desde la camara se ve el objetivo o hay bloques por medio. */
    private static boolean canSee(Vec3d eye, Vec3d targetPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return true;
        }
        BlockHitResult hit = client.world.raycast(new RaycastContext(eye, targetPos,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                client.player));
        return hit == null || hit.getType() == HitResult.Type.MISS;
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
                && client.player != null;
    }

    public void beginFrame() {
        frameCounter++;
        previewFrame = -1;
        // Con rafaga 1 esto es alternar uno a uno; subiendola, cada vista se lleva
        // varios frames seguidos, que es lo que necesitan los shaders temporales.
        long block = frameCounter / Math.max(1, settings().frameBurst);
        cameraFrame = canBroadcast() && mirror.hasFrame()
                && (block % settings().frameRatio == 0L);
        if (cameraFrame) {
            hideHud();
        }
        if (cameraFrame || !mirror.hasFrame()) {
            return;
        }
        // Uno de cada cuatro frames que no son de emision se dedica a refrescar el
        // monitor de una camara, rotando entre todas. Con nueve camaras a 60 fps
        // cada monitor se renueva un par de veces por segundo.
        if (previewsWanted && (MinecraftClient.getInstance().world != null || selfTestPreviews)
                && frameCounter % 4L == 1L) {
            List<CameraPoint> list = cameras();
            if (!list.isEmpty()) {
                previewCursor = (previewCursor + 1) % list.size();
                previewFrame = previewCursor;
                hideHud();
            }
        }
    }

    private void hideHud() {
        MinecraftClient client = MinecraftClient.getInstance();
        hudHiddenBackup = client.options.hudHidden;
        client.options.hudHidden = true;
    }

    public void endFrame() {
        if (cameraFrame || previewFrame >= 0) {
            MinecraftClient.getInstance().options.hudHidden = hudHiddenBackup;
        }
        cameraFrame = false;
        previewFrame = -1;
    }

    /**
     * Decide que se presenta en la ventana del jugador. Devuelve true si TVCam ya
     * se ha encargado y el juego no debe presentar su framebuffer.
     */
    public boolean presentToPlayerWindow(Framebuffer framebuffer) {
        try {
            if (previewFrame >= 0) {
                // El mundo ya se ha dibujado dentro del monitor: no hay nada que
                // copiar. Tu ventana repite tu ultima imagen para que no parpadee.
                previews.markDrawn(previewFrame);
                return mirror.present();
            }
            selfTestPresent();
            if (cameraFrame) {
                // Al final del frame: el plano lleva ya los rotulos del HUD y
                // ninguna pantalla, que se quedan fuera de los frames de camara.
                window.present(framebuffer);
                return mirror.present();
            }
            mirror.capture(framebuffer);
            return false;
        } catch (RuntimeException e) {
            failBroadcast(e);
            return false;
        }
    }

    private void failBroadcast(RuntimeException e) {
        TVCam.LOGGER.error("Fallo presentando el frame de camara, cierro la ventana", e);
        window.close();
        mirror.close();
        active = -1;
    }

    // ------------------------------------------------------------- autoprueba

    private boolean selfTestPresenting;
    /** Permite refrescar monitores sin mundo cargado, solo para la autoprueba. */
    public boolean selfTestPreviews;
    private int selfTestFrames;

    public void setSelfTestPresenting(boolean value) {
        selfTestPresenting = value;
    }

    /** Solo para la autoprueba: fuerza un frame de monitor para la camara dada. */
    public void selfTestForcePreview(int index) {
        previewFrame = index;
    }

    /** Solo para la autoprueba: pinta los monitores de un color inconfundible. */
    public void selfTestPaintPreviews() {
        for (int i = 0; i < cameras().size(); i++) {
            previews.fillWithColor(i, 0xFF00FF66);
        }
    }

    /**
     * Solo para la autoprueba: manda a la ventana de emision el contenido de un
     * monitor, para poder capturarlo y comprobar que de verdad lleva imagen.
     */
    public boolean selfTestShowPreview(int index) {
        Framebuffer buffer = previews.buffer(index);
        return buffer != null && window.present(buffer);
    }

    /** Solo para la autoprueba: manda el frame actual a la ventana de emision. */
    public void selfTestPresent() {
        if (!selfTestPresenting) {
            return;
        }
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (framebuffer != null && window.present(framebuffer)) {
            selfTestFrames++;
        }
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
        previews.close();
    }
}
