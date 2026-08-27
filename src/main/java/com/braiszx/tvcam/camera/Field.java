package com.braiszx.tvcam.camera;

import net.minecraft.util.math.Vec3d;

/**
 * Un campo de futbol: la zona donde TVCam busca la pelota, y opcionalmente las
 * dos porterias, para cantar los goles sin depender de lo que mande el servidor.
 *
 * <p>Existe porque con varios campos en el mismo mundo el seguimiento se liaba:
 * al marcar gol el plugin borra la pelota y crea otra, y al buscar la nueva podia
 * engancharse a la de la pista de al lado. Con un campo marcado solo se miran las
 * pelotas de dentro.
 */
public record Field(String name, double x, double y, double z, double radius,
                    Goal goalA, Goal goalB) {

    /** Una porteria, como una esfera alrededor de la linea de gol. */
    public record Goal(double x, double y, double z, double radius) {
        public Vec3d pos() {
            return new Vec3d(x, y, z);
        }

        public boolean contains(Vec3d point) {
            return point.squaredDistanceTo(pos()) <= radius * radius;
        }
    }

    public Vec3d center() {
        return new Vec3d(x, y, z);
    }

    /** Si un punto cae dentro del campo. Se es generoso en altura, no en planta. */
    public boolean contains(Vec3d point) {
        double dx = point.x - x;
        double dz = point.z - z;
        if (dx * dx + dz * dz > radius * radius) {
            return false;
        }
        return Math.abs(point.y - y) <= 48.0;
    }

    /** La porteria en la que esta el punto, o null. */
    public Goal goalAt(Vec3d point) {
        if (goalA != null && goalA.contains(point)) {
            return goalA;
        }
        if (goalB != null && goalB.contains(point)) {
            return goalB;
        }
        return null;
    }

    public Field withGoal(int which, Goal goal) {
        return which == 1
                ? new Field(name, x, y, z, radius, goal, goalB)
                : new Field(name, x, y, z, radius, goalA, goal);
    }
}
