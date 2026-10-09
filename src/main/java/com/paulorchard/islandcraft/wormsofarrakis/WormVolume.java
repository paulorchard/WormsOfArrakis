package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;

/**
 * The body of the worm as the player sees it: a cylinder (circle of 12 sides drawn, radius 3 blocks times the scale)
 * along the worm's axis, from its base to its top, tilting with the pitch towards the heading. Sizes are passed in,
 * nothing is fixed here, so larger worms work.
 */
final class WormVolume {

    private final Vector3d centre = new Vector3d();
    private final Vector3d axis = new Vector3d(0, 1, 0);
    private double halfLength;
    private double radius;

    /**
     * @param centre     the middle of the body (the entity position)
     * @param headingX   unit horizontal direction the worm tilts towards
     * @param headingZ   unit horizontal direction the worm tilts towards
     * @param pitch      tilt in radians, 0 upright
     * @param halfLength half the length of the body: 10 blocks times the scale
     * @param radius     3 blocks times the scale
     */
    void set(Vector3d centre, double headingX, double headingZ, double pitch, double halfLength, double radius) {
        this.centre.set(centre);
        this.axis.set(headingX * Math.sin(pitch), Math.cos(pitch), headingZ * Math.sin(pitch));
        this.halfLength = halfLength;
        this.radius = radius;
    }

    boolean contains(double x, double y, double z) {
        double dx = x - centre.x;
        double dy = y - centre.y;
        double dz = z - centre.z;
        double along = dx * axis.x + dy * axis.y + dz * axis.z;
        if (Math.abs(along) > halfLength) {
            return false;
        }
        double px = dx - along * axis.x;
        double py = dy - along * axis.y;
        double pz = dz - along * axis.z;
        return px * px + py * py + pz * pz <= radius * radius;
    }

    /** True if any of three points up a body of the given height standing at (x, feetY, z) is inside. */
    boolean touches(double x, double feetY, double z, double bodyHeight) {
        return contains(x, feetY + 0.2, z) || contains(x, feetY + bodyHeight * 0.5, z) || contains(x, feetY + bodyHeight - 0.2, z);
    }
}
