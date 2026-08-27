package com.braiszx.tvcam.camera;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Una posicion y una orientacion de camara en un instante dado. */
public record CameraPose(Vec3d pos, float yaw, float pitch) {

    /** Interpola entre dos poses, tomando siempre el camino corto en el giro. */
    public static CameraPose lerp(CameraPose from, CameraPose to, float t) {
        return new CameraPose(
                from.pos.lerp(to.pos, t),
                lerpAngle(from.yaw, to.yaw, t),
                MathHelper.lerp(t, from.pitch, to.pitch));
    }

    public static float lerpAngle(float from, float to, float t) {
        return from + MathHelper.wrapDegrees(to - from) * t;
    }

    /** Yaw y pitch necesarios para mirar desde {@code eye} hacia {@code target}. */
    public static CameraPose lookAt(Vec3d eye, Vec3d target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new CameraPose(eye, MathHelper.wrapDegrees(yaw), pitch);
    }
}
