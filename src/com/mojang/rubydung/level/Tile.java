package com.mojang.rubydung.level;

/**
 * Geometry for the two block textures that exist in rd-132211.
 *
 * The original class names are misleading: texture 0 is the green grass tile
 * and texture 1 is the grey stone tile. Chunk chooses texture 0 for the layer
 * at y == depth*2/3 (the surface) and texture 1 for everything else, which is
 * exactly what this port does.
 */
public class Tile {
    public static final int FACE_BOTTOM = 0;
    public static final int FACE_TOP = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_WEST = 4;
    public static final int FACE_EAST = 5;

    public static final float TILE = 0.0625F;
    public static final float INSET = 0.00005F;
    public static final float UV = TILE - 2.0F * INSET;

    public static final float SHADE_BOTTOM = 1.0F;
    public static final float SHADE_TOP = 1.0F;
    public static final float SHADE_NORTH = 0.8F;
    public static final float SHADE_SOUTH = 0.8F;
    public static final float SHADE_WEST = 0.6F;
    public static final float SHADE_EAST = 0.6F;

    public static void renderFace(MeshBuilder b, Level level, int x, int y, int z, int face, int tex) {
        float u0 = tex * TILE + INSET;
        float u1 = u0 + UV;
        float v0 = INSET;
        float v1 = v0 + UV;
        float x0 = x;
        float x1 = x + 1.0F;
        float y0 = y;
        float y1 = y + 1.0F;
        float z0 = z;
        float z1 = z + 1.0F;
        float br;

        switch (face) {
            case FACE_BOTTOM:
                br = level.getBrightness(x, y - 1, z) * SHADE_BOTTOM;
                b.quad(x0, y0, z1, u0, v1,
                        x0, y0, z0, u0, v0,
                        x1, y0, z0, u1, v0,
                        x1, y0, z1, u1, v1, br);
                break;
            case FACE_TOP:
                br = level.getBrightness(x, y, z) * SHADE_TOP;
                b.quad(x1, y1, z1, u1, v1,
                        x1, y1, z0, u1, v0,
                        x0, y1, z0, u0, v0,
                        x0, y1, z1, u0, v1, br);
                break;
            case FACE_NORTH:
                br = level.getBrightness(x, y, z - 1) * SHADE_NORTH;
                b.quad(x0, y1, z0, u1, v0,
                        x1, y1, z0, u0, v0,
                        x1, y0, z0, u0, v1,
                        x0, y0, z0, u1, v1, br);
                break;
            case FACE_SOUTH:
                br = level.getBrightness(x, y, z + 1) * SHADE_SOUTH;
                b.quad(x0, y1, z1, u0, v0,
                        x0, y0, z1, u0, v1,
                        x1, y0, z1, u1, v1,
                        x1, y1, z1, u1, v0, br);
                break;
            case FACE_WEST:
                br = level.getBrightness(x - 1, y, z) * SHADE_WEST;
                b.quad(x0, y1, z1, u1, v0,
                        x0, y1, z0, u0, v0,
                        x0, y0, z0, u0, v1,
                        x0, y0, z1, u1, v1, br);
                break;
            case FACE_EAST:
                br = level.getBrightness(x + 1, y, z) * SHADE_EAST;
                b.quad(x1, y0, z1, u0, v1,
                        x1, y0, z0, u1, v1,
                        x1, y1, z0, u1, v0,
                        x1, y1, z1, u0, v0, br);
                break;
            default:
                break;
        }
    }
}
