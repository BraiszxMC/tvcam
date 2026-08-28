package com.braiszx.tvcamrelay;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El servidor solo hace de centralita entre clientes de TVCam: no sabe nada de
 * camaras ni de video.
 *
 * <p>Quien tiene las camaras (y la ventana de OBS) pide un codigo, lo reparte, y
 * el resto se une con el. A partir de ahi el plugin se limita a llevar los
 * mensajes de un lado a otro: los mandos que pulsa un operador llegan al que
 * tiene las camaras, y este devuelve el estado para que todos vean lo mismo.
 *
 * <p>Que la centralita sea el servidor evita abrir puertos ni montar nada por
 * fuera: si estais jugando juntos, ya estais conectados a el.
 */
public final class TVCamRelay extends JavaPlugin implements Listener, PluginMessageListener {
    /** Canal por el que hablan mod y plugin. */
    public static final String CHANNEL = "tvcam:control";

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Una emision compartida: quien manda y quien mira. */
    private static final class Session {
        UUID host;
        String hostName;
        final List<UUID> operators = new ArrayList<>();
    }

    private final Map<String, Session> sessionsByCode = new HashMap<>();
    /** Para saber rapido en que emision esta cada jugador. */
    private final Map<UUID, String> codeByPlayer = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TVCamRelay listo: canal " + CHANNEL);
    }

    @Override
    public void onDisable() {
        sessionsByCode.clear();
        codeByPlayer.clear();
    }

    // --------------------------------------------------------------- mensajes

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        String message = new String(data, StandardCharsets.UTF_8);
        String[] parts = message.split("\\|", 2);
        String type = parts[0];
        String body = parts.length > 1 ? parts[1] : "";

        switch (type) {
            case "HOST" -> host(player);
            case "JOIN" -> join(player, body.trim().toUpperCase());
            case "LEAVE" -> leave(player, true);
            case "TO" -> relay(player, body);
            default -> { }
        }
    }

    private void host(Player player) {
        if (!player.hasPermission("tvcam.host")) {
            send(player, "ERR|No tienes permiso para compartir tus camaras.");
            return;
        }
        leave(player, false);
        String code = newCode();
        Session session = new Session();
        session.host = player.getUniqueId();
        session.hostName = player.getName();
        sessionsByCode.put(code, session);
        codeByPlayer.put(player.getUniqueId(), code);
        send(player, "CODE|" + code);
        getLogger().info(player.getName() + " comparte sus camaras con el codigo " + code);
    }

    private void join(Player player, String code) {
        if (!player.hasPermission("tvcam.join")) {
            send(player, "ERR|No tienes permiso para unirte a otras camaras.");
            return;
        }
        Session session = sessionsByCode.get(code);
        if (session == null) {
            send(player, "ERR|Ese codigo no existe. Revisalo con quien te lo paso.");
            return;
        }
        if (session.host.equals(player.getUniqueId())) {
            send(player, "ERR|Ese codigo es el tuyo.");
            return;
        }
        leave(player, false);
        session.operators.add(player.getUniqueId());
        codeByPlayer.put(player.getUniqueId(), code);
        send(player, "JOINED|" + session.hostName);

        Player host = Bukkit.getPlayer(session.host);
        if (host != null) {
            send(host, "OP+|" + player.getName());
        }
    }

    private void leave(Player player, boolean notify) {
        String code = codeByPlayer.remove(player.getUniqueId());
        if (code == null) {
            return;
        }
        Session session = sessionsByCode.get(code);
        if (session == null) {
            return;
        }
        if (session.host.equals(player.getUniqueId())) {
            // Si se va quien tiene las camaras, la emision se acaba para todos.
            for (UUID operator : session.operators) {
                Player other = Bukkit.getPlayer(operator);
                if (other != null) {
                    send(other, "ERR|Se ha ido quien tenia las camaras.");
                }
                codeByPlayer.remove(operator);
            }
            sessionsByCode.remove(code);
        } else {
            session.operators.remove(player.getUniqueId());
            Player host = Bukkit.getPlayer(session.host);
            if (host != null) {
                send(host, "OP-|" + player.getName());
            }
        }
        if (notify) {
            send(player, "LEFT|");
        }
    }

    /** Lleva un mensaje al otro lado: del que manda a los operadores y al reves. */
    private void relay(Player from, String body) {
        String code = codeByPlayer.get(from.getUniqueId());
        if (code == null) {
            return;
        }
        Session session = sessionsByCode.get(code);
        if (session == null) {
            return;
        }
        String payload = "FROM|" + from.getName() + "|" + body;
        if (session.host.equals(from.getUniqueId())) {
            for (UUID operator : session.operators) {
                Player other = Bukkit.getPlayer(operator);
                if (other != null) {
                    send(other, payload);
                }
            }
        } else {
            Player host = Bukkit.getPlayer(session.host);
            if (host != null) {
                send(host, payload);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        leave(event.getPlayer(), false);
    }

    // ---------------------------------------------------------------- utiles

    private void send(Player player, String message) {
        player.sendPluginMessage(this, CHANNEL, message.getBytes(StandardCharsets.UTF_8));
    }

    /** Codigo corto sin letras que se confundan: nada de O/0 ni I/1. */
    private String newCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            code = builder.toString();
        } while (sessionsByCode.containsKey(code));
        return code;
    }
}
