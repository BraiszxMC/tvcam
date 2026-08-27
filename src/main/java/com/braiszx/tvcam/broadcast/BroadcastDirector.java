package com.braiszx.tvcam.broadcast;

import com.braiszx.tvcam.TVCam;
import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.Field;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que se cuenta encima de la emision: por ahora, cantar los goles.
 *
 * <p>Hay dos maneras de enterarse de un gol, porque ninguna vale siempre:
 *
 * <ul>
 *   <li><b>Por el titulo del servidor.</b> BlockBall canta el gol con un titulo
 *       que lleva el marcador y un subtitulo con el autor. Es exacto, pero el
 *       plugin solo se lo manda <b>a los jugadores de los dos equipos</b>: si
 *       retransmites sin jugar, no te llega nada.
 *   <li><b>Por la porteria.</b> Marcas las dos porterias del campo y TVCam canta
 *       el gol cuando ve entrar la pelota, y como autor pone al ultimo que la
 *       toco. Funciona aunque estes de espectador y no dependas del idioma del
 *       servidor.
 * </ul>
 */
public final class BroadcastDirector {
    /** "3 : 1", en cualquier idioma. Asi se reconoce el titulo de gol. */
    private static final Pattern SCORE = Pattern.compile("^\\s*(\\d{1,3})\\s*[:\\-]\\s*(\\d{1,3})\\s*$");
    /** Ventana en la que un subtitulo se considera parte del mismo aviso de gol. */
    private static final long TITLE_PAIR_NANOS = 1_500_000_000L;
    /** Para no cantar dos veces el mismo gol por las dos vias. */
    private static final long GOAL_COOLDOWN_NANOS = 5_000_000_000L;
    /** A que distancia se considera que un jugador ha tocado la pelota. */
    private static final double TOUCH_DISTANCE = 2.6;

    private final GoalFlash goalFlash = new GoalFlash();

    private String pendingScore;
    private long pendingScoreNanos;
    private long lastGoalNanos;

    private String lastToucher;
    private long lastTouchNanos;
    private boolean ballWasInGoal;

    public GoalFlash goalFlash() {
        return goalFlash;
    }

    public String lastToucher() {
        return lastToucher;
    }

    // ------------------------------------------------- via 1: titulo del servidor

    public void onTitle(Text text) {
        Matcher matcher = SCORE.matcher(text.getString().trim());
        if (matcher.matches()) {
            pendingScore = matcher.group(1) + " - " + matcher.group(2);
            pendingScoreNanos = System.nanoTime();
        }
    }

    public void onSubtitle(Text text) {
        if (pendingScore == null || System.nanoTime() - pendingScoreNanos > TITLE_PAIR_NANOS) {
            return;
        }
        String scorer = findPlayerName(text.getString());
        announceGoal(scorer != null ? scorer : lastToucher, pendingScore);
        pendingScore = null;
    }

    /**
     * Saca el autor del subtitulo buscando dentro un nombre de la lista de
     * jugadores, en vez de intentar entender la frase: asi da igual el idioma en
     * el que el servidor tenga configurado el mensaje.
     */
    private String findPlayerName(String sentence) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }
        String best = null;
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            String name = player.getGameProfile().name();
            if (name != null && !name.isBlank() && sentence.contains(name)
                    && (best == null || name.length() > best.length())) {
                best = name;
            }
        }
        return best;
    }

    // ---------------------------------------------------- via 2: ver la porteria

    /** Llamado cada tick con la pelota que se esta siguiendo (o null). */
    public void tick(Entity ball, Field field) {
        if (ball == null) {
            ballWasInGoal = false;
            return;
        }
        Vec3d ballPos = ball.getEntityPos();
        rememberToucher(ballPos);

        if (field == null) {
            return;
        }
        boolean inGoal = field.goalAt(ballPos) != null;
        if (inGoal && !ballWasInGoal) {
            announceGoal(lastToucher, null);
        }
        ballWasInGoal = inGoal;
    }

    /** El ultimo jugador que estuvo pegado a la pelota es el que la toco. */
    private void rememberToucher(Vec3d ballPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        double best = TOUCH_DISTANCE * TOUCH_DISTANCE;
        String found = null;
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            double distance = player.getEntityPos().squaredDistanceTo(ballPos);
            if (distance < best) {
                best = distance;
                found = player.getGameProfile().name();
            }
        }
        if (found != null) {
            lastToucher = found;
            lastTouchNanos = System.nanoTime();
        }
    }

    // ------------------------------------------------------------------ comun

    private void announceGoal(String scorer, String score) {
        long now = System.nanoTime();
        if (now - lastGoalNanos < GOAL_COOLDOWN_NANOS) {
            return;
        }
        lastGoalNanos = now;
        // Un toque de hace medio minuto ya no cuenta como autor del gol.
        boolean recentTouch = now - lastTouchNanos < 30_000_000_000L;
        String author = (scorer == null || (scorer.equals(lastToucher) && !recentTouch)) ? null : scorer;
        goalFlash.show(author, score);
        TVCam.LOGGER.info("Gol cantado: autor={} marcador={}", author, score);
    }

    /** Dibuja los rotulos sobre la emision. */
    public void render(DrawContext context) {
        goalFlash.render(context);
    }

    /** Para probar el rotulo sin esperar a que alguien marque. */
    public void testGoal() {
        MinecraftClient client = MinecraftClient.getInstance();
        String name = client.player != null ? client.player.getGameProfile().name() : "Jugador";
        lastGoalNanos = 0L;
        announceGoal(name, "1 - 0");
    }

    public static BroadcastDirector get() {
        return CameraDirector.get().broadcast();
    }
}
