package com.braiszx.tvcam.camera;

/**
 * A quien enfoca <b>una</b> camara. Cada camara lleva el suyo, para poder tener a
 * la vez una siguiendo la pelota y otra pegada a un jugador concreto.
 */
public final class TargetSpec {
    public enum Kind {
        /** Sigue al objetivo general de la retransmision (lo que marques con /tvcam ball). */
        GLOBAL,
        /** Esta camara no sigue a nadie aunque este en modo seguir. */
        NINGUNO,
        /** La pelota del campo activo. */
        PELOTA,
        /** Un jugador concreto, por nombre. */
        JUGADOR
    }

    public Kind kind = Kind.GLOBAL;
    public String player;

    public TargetSpec() {
    }

    public static TargetSpec global() {
        return new TargetSpec();
    }

    public static TargetSpec ball() {
        TargetSpec spec = new TargetSpec();
        spec.kind = Kind.PELOTA;
        return spec;
    }

    public static TargetSpec player(String name) {
        TargetSpec spec = new TargetSpec();
        spec.kind = Kind.JUGADOR;
        spec.player = name;
        return spec;
    }

    public static TargetSpec none() {
        TargetSpec spec = new TargetSpec();
        spec.kind = Kind.NINGUNO;
        return spec;
    }

    public TargetSpec copy() {
        TargetSpec spec = new TargetSpec();
        spec.kind = kind;
        spec.player = player;
        return spec;
    }

    public String describe() {
        return switch (kind) {
            case GLOBAL -> "el general";
            case NINGUNO -> "nadie";
            case PELOTA -> "la pelota";
            case JUGADOR -> player == null ? "un jugador" : player;
        };
    }

    /** Etiqueta corta para los botones de la mesa. */
    public String shortLabel() {
        return switch (kind) {
            case GLOBAL -> "general";
            case NINGUNO -> "nadie";
            case PELOTA -> "pelota";
            case JUGADOR -> player == null ? "jugador" : player;
        };
    }
}
