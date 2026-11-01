package com.mojang.rubydung;

/**
 * A block hit. face is the face that was entered:
 * 0 = -Y (bottom), 1 = +Y (top), 2 = -Z, 3 = +Z, 4 = -X, 5 = +X.
 * This is the same numbering as the original Tile.renderFace.
 */
public class HitResult {
    public final int x;
    public final int y;
    public final int z;
    public final int face;

    public HitResult(int x, int y, int z, int face) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
    }
}
