package com.braiszx.tvcam.net;

import com.braiszx.tvcam.TVCam;
import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraPoint;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Control remoto: varias personas manejando las mismas camaras.
 *
 * <p>Quien tiene las camaras y la ventana de OBS es el <b>anfitrion</b>: pide un
 * codigo y lo reparte. Los demas se unen con ese codigo y se convierten en
 * <b>operadores</b>: lo que pulsan viaja al anfitrion, que es quien lo ejecuta.
 * Solo se mandan ordenes y nombres de camara, nunca video, asi que cuesta nada.
 *
 * <p>Hace falta el plugin TVCamRelay en el servidor. Sin el, los mensajes no
 * llegan a ninguna parte y el mod sigue funcionando como siempre, para ti solo.
 */
public final class RemoteControl {
    private static final RemoteControl INSTANCE = new RemoteControl();

    public static RemoteControl get() {
        return INSTANCE;
    }

    public enum Role {
        SOLO,
        ANFITRION,
        OPERADOR
    }

    /** Una camara vista desde el mando de un operador: solo su nombre. */
    public record RemoteCamera(String name) {
    }

    private Role role = Role.SOLO;
    private String code = "";
    private String hostName = "";
    private String status = "";
    private final List<String> operators = new ArrayList<>();

    /** Lo que el operador sabe de las camaras del anfitrion. */
    private final List<RemoteCamera> remoteCameras = new ArrayList<>();
    private int remoteActive = -1;
    private boolean remoteAuto;

    private long lastStateSentNanos;

    private RemoteControl() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ControlPayload.ID, ControlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ControlPayload.ID, ControlPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ControlPayload.ID,
                (payload, context) -> context.client().execute(() -> INSTANCE.receive(payload.message())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.reset());
    }

    // ----------------------------------------------------------------- estado

    public Role role() {
        return role;
    }

    public String code() {
        return code;
    }

    public String hostName() {
        return hostName;
    }

    public String status() {
        return status;
    }

    public List<String> operators() {
        return operators;
    }

    public List<RemoteCamera> remoteCameras() {
        return remoteCameras;
    }

    public int remoteActive() {
        return remoteActive;
    }

    public boolean remoteAuto() {
        return remoteAuto;
    }

    /** true si el servidor tiene el plugin: se sabe porque contesta. */
    public boolean serverAnswered() {
        return role != Role.SOLO || !status.isEmpty();
    }

    private void reset() {
        role = Role.SOLO;
        code = "";
        hostName = "";
        operators.clear();
        remoteCameras.clear();
        remoteActive = -1;
    }

    // ---------------------------------------------------------------- ordenes

    public void share() {
        status = "Pidiendo codigo...";
        send("HOST");
    }

    public void join(String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return;
        }
        status = "Uniendome...";
        send("JOIN|" + wanted.trim().toUpperCase());
    }

    public void leave() {
        send("LEAVE");
        reset();
        status = "";
    }

    /** Un operador pide poner una camara al aire. */
    public void requestCut(int index) {
        send("TO|CUT|" + index);
    }

    /** Un operador enciende o apaga el realizador automatico del anfitrion. */
    public void requestAuto(boolean enabled) {
        send("TO|AUTO|" + (enabled ? "1" : "0"));
    }

    /** Un operador manda seguir la pelota. */
    public void requestBall() {
        send("TO|BALL");
    }

    private void send(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            status = "No estas en ningun servidor.";
            return;
        }
        if (!ClientPlayNetworking.canSend(ControlPayload.ID)) {
            status = "Este servidor no tiene el plugin TVCamRelay.";
            return;
        }
        ClientPlayNetworking.send(new ControlPayload(message));
    }

    /**
     * El anfitrion cuenta a los operadores como esta la cosa. Se manda al cambiar
     * algo y como mucho una vez por segundo: son cuatro palabras, pero no hay que
     * abusar.
     */
    public void publishState(boolean force) {
        if (role != Role.ANFITRION) {
            return;
        }
        long now = System.nanoTime();
        if (!force && now - lastStateSentNanos < 1_000_000_000L) {
            return;
        }
        lastStateSentNanos = now;
        CameraDirector director = CameraDirector.get();
        StringBuilder names = new StringBuilder();
        for (CameraPoint camera : director.cameras()) {
            if (names.length() > 0) {
                names.append(';');
            }
            names.append(camera.name().replace(';', ' ').replace('|', ' '));
        }
        send("TO|STATE|" + director.activeIndex() + "|"
                + (director.settings().autoDirector ? 1 : 0) + "|" + names);
    }

    // --------------------------------------------------------------- recepcion

    private void receive(String message) {
        String[] parts = message.split("\\|", 3);
        String type = parts[0];
        switch (type) {
            case "CODE" -> {
                role = Role.ANFITRION;
                code = parts.length > 1 ? parts[1] : "";
                status = "Comparte este codigo: " + code;
                chat("Tus camaras se comparten con el codigo " + code, Formatting.GREEN);
                publishState(true);
            }
            case "JOINED" -> {
                role = Role.OPERADOR;
                hostName = parts.length > 1 ? parts[1] : "?";
                status = "Manejando las camaras de " + hostName;
                chat("Te has unido a las camaras de " + hostName, Formatting.GREEN);
            }
            case "LEFT" -> {
                reset();
                status = "";
            }
            case "ERR" -> {
                status = parts.length > 1 ? parts[1] : "Error";
                chat(status, Formatting.RED);
            }
            case "OP+" -> {
                String name = parts.length > 1 ? parts[1] : "?";
                operators.add(name);
                chat(name + " se ha unido a tus camaras", Formatting.AQUA);
                publishState(true);
            }
            case "OP-" -> {
                String name = parts.length > 1 ? parts[1] : "?";
                operators.remove(name);
            }
            case "FROM" -> {
                String from = parts.length > 1 ? parts[1] : "?";
                handle(from, parts.length > 2 ? parts[2] : "");
            }
            default -> { }
        }
    }

    /** Lo que llega del otro lado ya con nombre y apellidos. */
    private void handle(String from, String body) {
        String[] parts = body.split("\\|");
        if (parts.length == 0) {
            return;
        }
        CameraDirector director = CameraDirector.get();
        switch (parts[0]) {
            case "STATE" -> {
                if (role != Role.OPERADOR) {
                    return;
                }
                remoteActive = parts.length > 1 ? parseInt(parts[1], -1) : -1;
                remoteAuto = parts.length > 2 && "1".equals(parts[2]);
                remoteCameras.clear();
                if (parts.length > 3 && !parts[3].isBlank()) {
                    for (String name : parts[3].split(";")) {
                        remoteCameras.add(new RemoteCamera(name));
                    }
                }
            }
            // A partir de aqui, ordenes que solo obedece el anfitrion.
            case "CUT" -> {
                if (role == Role.ANFITRION) {
                    director.cut(parseInt(parts.length > 1 ? parts[1] : "-1", -1));
                    chat(from + " ha cortado de plano", Formatting.GRAY);
                    publishState(true);
                }
            }
            case "AUTO" -> {
                if (role == Role.ANFITRION) {
                    director.settings().autoDirector = parts.length > 1 && "1".equals(parts[1]);
                    director.saveSettings();
                    publishState(true);
                }
            }
            case "BALL" -> {
                if (role == Role.ANFITRION) {
                    director.target().followBall();
                    publishState(true);
                }
            }
            default -> { }
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void chat(String text, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("TVCam: " + text).formatted(color), false);
        }
        TVCam.LOGGER.info("[remoto] {}", text);
    }
}
