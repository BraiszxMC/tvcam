package com.braiszx.tvcam.camera;

import net.minecraft.util.math.Vec3d;

/**
 * Una camara colocada en el mundo: donde esta, hacia donde mira por defecto, como
 * se comporta respecto al objetivo y cuanto zoom lleva.
 *
 * <p>Los campos nuevos pueden faltar en ficheros guardados por versiones viejas,
 * por eso {@link #normalized()} rellena los huecos.
 */
public record CameraPoint(String name, double x, double y, double z, float yaw, float pitch,
                          CameraMode mode, float zoom, double offsetX, double offsetY, double offsetZ) {

    public static CameraPoint fixed(String name, Vec3d pos, float yaw, float pitch) {
        return new CameraPoint(name, pos.x, pos.y, pos.z, yaw, pitch, CameraMode.FIJA, 1.0f, 0, 0, 0);
    }

    public Vec3d pos() {
        return new Vec3d(x, y, z);
    }

    /** Desplazamiento respecto al objetivo, usado por el modo ACOMPANAR. */
    public Vec3d offset() {
        return new Vec3d(offsetX, offsetY, offsetZ);
    }

    /** Rellena lo que falte al leer un fichero guardado por una version anterior. */
    public CameraPoint normalized() {
        CameraMode safeMode = mode == null ? CameraMode.FIJA : mode;
        float safeZoom = zoom <= 0.0f ? 1.0f : zoom;
        if (safeMode == mode && safeZoom == zoom) {
            return this;
        }
        return new CameraPoint(name, x, y, z, yaw, pitch, safeMode, safeZoom, offsetX, offsetY, offsetZ);
    }

    public CameraPoint withName(String newName) {
        return new CameraPoint(newName, x, y, z, yaw, pitch, mode, zoom, offsetX, offsetY, offsetZ);
    }

    public CameraPoint withMode(CameraMode newMode, Vec3d newOffset) {
        return new CameraPoint(name, x, y, z, yaw, pitch, newMode, zoom,
                newOffset.x, newOffset.y, newOffset.z);
    }

    public CameraPoint withZoom(float newZoom) {
        return new CameraPoint(name, x, y, z, yaw, pitch, mode, newZoom, offsetX, offsetY, offsetZ);
    }
}
