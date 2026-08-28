package com.braiszx.tvcam.camera;

import net.minecraft.util.math.Vec3d;

/**
 * Una camara colocada en el mundo, con todos sus ajustes propios: donde esta,
 * como se comporta, a quien enfoca, cuanto zoom lleva y con que pulso sigue.
 *
 * <p>Es una clase y no un record a proposito: cada ajuste nuevo se anade sin
 * romper los ficheros guardados por versiones anteriores, y los que puedan
 * heredarse del ajuste general se guardan como objeto nulo, que es justo lo que
 * significa "usa el general".
 */
public final class CameraPoint {
    public String name = "Camara";
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;

    public CameraMode mode = CameraMode.FIJA;
    public float zoom = 1.0f;

    /** Desplazamiento respecto al objetivo, usado por el modo ACOMPANAR. */
    public double offsetX;
    public double offsetY;
    public double offsetZ;

    /** A quien enfoca esta camara en concreto. */
    public TargetSpec target = TargetSpec.global();

    /** null = usa el ajuste general. */
    public Boolean autoZoom;
    /** null = usa el ajuste general. 0-100. */
    public Integer smoothing;

    /**
     * true = el realizador automatico no corta nunca a esta camara. Tu si puedes
     * ponerla al aire a mano. Sirve para tener planos de recurso guardados que no
     * quieres que salgan solos.
     */
    public boolean autoSkip;

    public CameraPoint() {
    }

    public static CameraPoint at(String name, Vec3d pos, float yaw, float pitch) {
        CameraPoint camera = new CameraPoint();
        camera.name = name;
        camera.x = pos.x;
        camera.y = pos.y;
        camera.z = pos.z;
        camera.yaw = yaw;
        camera.pitch = pitch;
        return camera;
    }

    public String name() {
        return name;
    }

    public Vec3d pos() {
        return new Vec3d(x, y, z);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public CameraMode mode() {
        return mode;
    }

    public float zoom() {
        return zoom;
    }

    public Vec3d offset() {
        return new Vec3d(offsetX, offsetY, offsetZ);
    }

    public TargetSpec target() {
        return target == null ? (target = TargetSpec.global()) : target;
    }

    public void moveTo(Vec3d pos, float newYaw, float newPitch) {
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.yaw = newYaw;
        this.pitch = newPitch;
    }

    public void aimAt(float newYaw, float newPitch) {
        this.yaw = newYaw;
        this.pitch = newPitch;
    }

    public void setOffset(Vec3d offset) {
        this.offsetX = offset.x;
        this.offsetY = offset.y;
        this.offsetZ = offset.z;
    }

    public CameraPoint copy() {
        CameraPoint copy = new CameraPoint();
        copy.name = name;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.yaw = yaw;
        copy.pitch = pitch;
        copy.mode = mode;
        copy.zoom = zoom;
        copy.offsetX = offsetX;
        copy.offsetY = offsetY;
        copy.offsetZ = offsetZ;
        copy.target = target().copy();
        copy.autoZoom = autoZoom;
        copy.smoothing = smoothing;
        copy.autoSkip = autoSkip;
        return copy;
    }

    /** Rellena lo que falte al leer un fichero guardado por una version anterior. */
    public CameraPoint normalized() {
        if (mode == null) {
            mode = CameraMode.FIJA;
        }
        if (zoom <= 0.0f) {
            zoom = 1.0f;
        }
        if (target == null) {
            // Las camaras de antes no tenian objetivo propio: seguian al general.
            target = TargetSpec.global();
        }
        if (name == null || name.isBlank()) {
            name = "Camara";
        }
        return this;
    }
}
