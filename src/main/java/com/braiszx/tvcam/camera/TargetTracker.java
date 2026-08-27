package com.braiszx.tvcam.camera;

import com.braiszx.tvcam.TVCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * A quien enfocan las camaras: la pelota, un jugador o la entidad a la que apuntes.
 *
 * <p>La pelota de BlockBall no es una entidad normal del servidor: el plugin la
 * envia por paquetes como un <b>armor stand invisible con un item en la cabeza</b>
 * (y una entidad aparte, sin dibujo, para las colisiones). Eso llega igual al
 * cliente, asi que podemos reconocerla y seguirla sin que el servidor tenga el mod.
 *
 * <p>Cuando marcan gol el plugin borra la pelota y crea otra: por eso, si el
 * objetivo desaparece, lo volvemos a buscar solo.
 */
public final class TargetTracker {
    /** Cada cuantos ticks se reintenta encontrar la pelota si se ha perdido. */
    private static final int REACQUIRE_TICKS = 10;
    /** Angulo maximo, en grados, para considerar que estas apuntando a una entidad. */
    private static final double LOCK_ON_CONE_DEGREES = 6.0;
    private static final double LOCK_ON_RANGE = 160.0;

    public enum Kind {
        NINGUNO,
        PELOTA,
        JUGADOR,
        ENTIDAD
    }

    private Kind kind = Kind.NINGUNO;
    private String playerName;
    private int entityId = -1;
    private int ticksSinceSearch;

    public Kind kind() {
        return kind;
    }

    public String playerName() {
        return playerName;
    }

    /** Etiqueta corta para los botones de la mesa. */
    public String shortLabel() {
        return switch (kind) {
            case NINGUNO -> "nadie";
            case PELOTA -> "pelota";
            case JUGADOR -> playerName == null ? "jugador" : playerName;
            case ENTIDAD -> "marcado";
        };
    }

    public String describe() {
        return switch (kind) {
            case NINGUNO -> "sin objetivo";
            case PELOTA -> "la pelota";
            case JUGADOR -> "el jugador " + playerName;
            case ENTIDAD -> "una entidad marcada";
        };
    }

    public void clear() {
        kind = Kind.NINGUNO;
        playerName = null;
        entityId = -1;
    }

    public void followBall() {
        clear();
        kind = Kind.PELOTA;
        ticksSinceSearch = REACQUIRE_TICKS;
    }

    public void followPlayer(String name) {
        clear();
        kind = Kind.JUGADOR;
        playerName = name;
    }

    public boolean followEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        clear();
        kind = Kind.ENTIDAD;
        entityId = entity.getId();
        return true;
    }

    // ------------------------------------------------------------- resolucion

    /**
     * Resuelve el objetivo de <b>una</b> camara: si dice GLOBAL usa el objetivo
     * general de la retransmision, y si no, el suyo propio.
     */
    public Entity resolve(TargetSpec spec) {
        if (spec == null || spec.kind == TargetSpec.Kind.GLOBAL) {
            return resolve();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            return null;
        }
        return switch (spec.kind) {
            case NINGUNO, GLOBAL -> null;
            case PELOTA -> resolveBall(world, client);
            case JUGADOR -> findPlayer(world, spec.player);
        };
    }

    private Entity findPlayer(ClientWorld world, String name) {
        if (name == null) {
            return null;
        }
        return world.getPlayers().stream()
                .filter(p -> name.equalsIgnoreCase(p.getGameProfile().name()))
                .findFirst().orElse(null);
    }

    /** La entidad a la que apunta el objetivo general, o null. */
    public Entity resolve() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || kind == Kind.NINGUNO) {
            return null;
        }
        return switch (kind) {
            case JUGADOR -> findPlayer(world, playerName);
            case ENTIDAD -> valid(world.getEntityById(entityId));
            case PELOTA -> resolveBall(world, client);
            case NINGUNO -> null;
        };
    }

    private Entity valid(Entity entity) {
        return entity != null && !entity.isRemoved() ? entity : null;
    }

    private Entity resolveBall(ClientWorld world, MinecraftClient client) {
        Entity current = valid(world.getEntityById(entityId));
        if (current != null) {
            return current;
        }
        // La pelota anterior ya no existe (gol, reinicio de partida...): buscamos otra,
        // pero no en todos los frames, que recorrer las entidades no es gratis.
        if (ticksSinceSearch++ < REACQUIRE_TICKS) {
            return null;
        }
        ticksSinceSearch = 0;
        Field field = CameraDirector.get().activeField();
        Vec3d from = field != null ? field.center()
                : (client.player != null ? client.player.getEntityPos() : Vec3d.ZERO);
        Entity found = findBall(world, from, field);
        if (found != null) {
            entityId = found.getId();
            TVCam.LOGGER.info("Pelota encontrada (entidad {})", entityId);
        }
        return found;
    }

    /**
     * Busca la pelota mas cercana: un armor stand invisible con algo en la cabeza,
     * que es exactamente como BlockBall dibuja el balon. Si hay un campo marcado,
     * ignora las pelotas de fuera.
     */
    public static Entity findBall(ClientWorld world, Vec3d near, Field field) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity armorStand)) {
                continue;
            }
            if (!armorStand.isInvisible()) {
                continue;
            }
            if (armorStand.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
                continue;
            }
            if (field != null && !field.contains(entity.getEntityPos())) {
                // Con varios campos en el mismo mundo, solo cuentan las pelotas del
                // campo con el que estas trabajando.
                continue;
            }
            double distance = entity.squaredDistanceTo(near);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    /** La entidad a la que esta apuntando el jugador, para marcarla como objetivo. */
    public static Entity entityUnderCrosshair(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0f);
        double bestAngle = Math.cos(Math.toRadians(LOCK_ON_CONE_DEGREES));
        double bestDistance = Double.MAX_VALUE;
        Entity best = null;
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) {
                continue;
            }
            Vec3d toEntity = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = toEntity.length();
            if (distance > LOCK_ON_RANGE || distance < 0.01) {
                continue;
            }
            double alignment = toEntity.multiply(1.0 / distance).dotProduct(look);
            if (alignment < bestAngle) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    /** El punto exacto al que encuadran las camaras. */
    public Vec3d aimPoint(Entity entity, float tickDelta, double verticalOffset) {
        Vec3d pos = entity.getLerpedPos(tickDelta);
        double height = entity.getBoundingBox().getLengthY();
        double centerY = pos.y + height * 0.5;
        if (entity instanceof PlayerEntity player) {
            // A los jugadores se les encuadra por la cabeza, no por la barriga.
            centerY = pos.y + player.getStandingEyeHeight() * 0.9;
        }
        return new Vec3d(pos.x, centerY + verticalOffset, pos.z);
    }
}
