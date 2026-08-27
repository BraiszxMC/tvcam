package com.braiszx.tvcam.camera;

/** Ajustes generales, guardados junto a las camaras en config/tvcam.json. */
public final class TVCamSettings {
    /** Tamano de la ventana de camara. Es lo que captura OBS. */
    public int windowWidth = 1920;
    public int windowHeight = 1080;

    /** 1 de cada N frames va a la ventana de camara. 2 = mitad y mitad. */
    public int frameRatio = 2;

    /** Duracion del travelling al cambiar de camara, en milisegundos. 0 = corte seco. */
    public int transitionMillis = 700;

    /** Suavidad del seguimiento, 0 = clavado al objetivo, 100 = muy perezoso. */
    public int smoothing = 45;

    /** Ajuste fino de la altura a la que se encuadra el objetivo. */
    public double aimOffset = 0.3;

    /** Realizador automatico: corta solo a la camara que mejor ve la jugada. */
    public boolean autoDirector = false;
    /** Segundos minimos que aguanta un plano antes de que el realizador corte a otro. */
    public double autoMinShotSeconds = 4.0;

    public TVCamSettings normalized() {
        windowWidth = Math.clamp(windowWidth, 320, 7680);
        windowHeight = Math.clamp(windowHeight, 180, 4320);
        frameRatio = Math.clamp(frameRatio, 2, 10);
        transitionMillis = Math.clamp(transitionMillis, 0, 10000);
        smoothing = Math.clamp(smoothing, 0, 100);
        aimOffset = Math.clamp(aimOffset, -5.0, 5.0);
        autoMinShotSeconds = Math.clamp(autoMinShotSeconds, 1.0, 60.0);
        return this;
    }

    /**
     * Cuanto se acerca la camara al angulo deseado en un segundo. Con suavizado 0
     * el giro es instantaneo; cuanto mas alto, mas tarda en alcanzarlo, como el
     * pulso de un camara de verdad.
     */
    public double followResponse() {
        if (smoothing <= 0) {
            return Double.MAX_VALUE;
        }
        return 12.0 * (1.0 - smoothing / 100.0) + 0.6;
    }
}
