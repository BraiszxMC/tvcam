package com.braiszx.tvcam.camera;

/** Como se comporta una camara respecto al objetivo (la pelota, un jugador...). */
public enum CameraMode {
    /** No se mueve ni gira. El plano fijo de toda la vida. */
    FIJA,
    /** No se mueve de su sitio pero gira siguiendo al objetivo, como una camara sobre tripode. */
    SEGUIR,
    /** Se mueve con el objetivo manteniendo la distancia con la que se creo, y lo encuadra. */
    ACOMPANAR;

    public boolean needsTarget() {
        return this != FIJA;
    }

    public static CameraMode parse(String value) {
        for (CameraMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }
}
