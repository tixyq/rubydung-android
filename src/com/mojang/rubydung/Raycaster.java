package com.mojang.rubydung;

import com.mojang.rubydung.level.Level;

/**
 * Voxel DDA raycast used instead of the original GL_SELECT picking.
 * No Android or OpenGL types, so it is unit-testable on a desktop JVM.
 */
public class Raycaster {
    public static HitResult cast(Level level, float px, float py, float pz,
                                 float yRot, float xRot, int reach) {
        float pitch = xRot * (float) Math.PI / 180.0F;
        float yaw = yRot * (float) Math.PI / 180.0F;
        float dx = (float) Math.sin(yaw) * (float) Math.cos(pitch);
        float dy = -(float) Math.sin(pitch);
        float dz = -(float) Math.cos(yaw) * (float) Math.cos(pitch);
        return castDirection(level, px, py, pz, dx, dy, dz, reach);
    }

    /**
     * MCPE freeform picking: unproject the finger position through the same
     * camera used by the renderer, then walk that ray through the voxels.
     * The eye is at the player position; the renderer puts the camera
     * CAMERA_BACK units behind it, so the ray starts at the camera's near
     * plane rather than inside a wall behind the player.
     */
    public static HitResult castScreen(Level level, float eyeX, float eyeY, float eyeZ,
                                       float yRot, float xRot,
                                       float screenX, float screenY,
                                       int width, int height,
                                       float tanHalfFov, float cameraBack, int reach) {
        if (width <= 0 || height <= 0) {
            return cast(level, eyeX, eyeY, eyeZ, yRot, xRot, reach);
        }
        if (screenX < 0.0F) screenX = 0.0F;
        if (screenX > width) screenX = width;
        if (screenY < 0.0F) screenY = 0.0F;
        if (screenY > height) screenY = height;

        float pitch = xRot * (float) Math.PI / 180.0F;
        float yaw = yRot * (float) Math.PI / 180.0F;
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);

        // Camera position: the renderer translates the view back along its
        // local -Z by cameraBack before applying the player rotation.
        float fx = sy * cp;
        float fy = -sp;
        float fz = -cy * cp;
        float camX = eyeX - fx * cameraBack;
        float camY = eyeY - fy * cameraBack;
        float camZ = eyeZ - fz * cameraBack;

        // Screen point in camera space (x right, y up, -Z forward).
        float aspect = (float) width / (float) height;
        float nx = (2.0F * screenX / width - 1.0F) * tanHalfFov * aspect;
        float ny = (1.0F - 2.0F * screenY / height) * tanHalfFov;
        float camLen = (float) Math.sqrt(nx * nx + ny * ny + 1.0F);

        // Rotate the camera-space direction into world space.
        float dx = cy * nx + sy * sp * ny + sy * cp;
        float dy = cp * ny - sp;
        float dz = sy * nx - cy * sp * ny - cy * cp;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6F) {
            return null;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        // Start on the near plane, exactly like glhUnProjectf at z=0.
        float near = 0.1F * camLen;
        float ox = camX + dx * near;
        float oy = camY + dy * near;
        float oz = camZ + dz * near;
        return castDirection(level, ox, oy, oz, dx, dy, dz, reach);
    }

    public static HitResult castDirection(Level level, float px, float py, float pz,
                                          float dx, float dy, float dz, int reach) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6F) {
            return null;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        int x = (int) Math.floor(px);
        int y = (int) Math.floor(py);
        int z = (int) Math.floor(pz);
        if (level.isSolidTile(x, y, z)) {
            // The camera ended up inside a block (easy to do when building
            // around yourself). Return the containing block so a held touch
            // can still break it; the desktop build could use the R key here.
            return new HitResult(x, y, z, 1);
        }

        int stepX = dx > 0.0F ? 1 : (dx < 0.0F ? -1 : 0);
        int stepY = dy > 0.0F ? 1 : (dy < 0.0F ? -1 : 0);
        int stepZ = dz > 0.0F ? 1 : (dz < 0.0F ? -1 : 0);

        float tDeltaX = stepX == 0 ? Float.MAX_VALUE : Math.abs(1.0F / dx);
        float tDeltaY = stepY == 0 ? Float.MAX_VALUE : Math.abs(1.0F / dy);
        float tDeltaZ = stepZ == 0 ? Float.MAX_VALUE : Math.abs(1.0F / dz);

        float tMaxX;
        float tMaxY;
        float tMaxZ;
        if (stepX > 0) {
            tMaxX = (x + 1 - px) / dx;
        } else if (stepX < 0) {
            tMaxX = (x - px) / dx;
        } else {
            tMaxX = Float.MAX_VALUE;
        }
        if (stepY > 0) {
            tMaxY = (y + 1 - py) / dy;
        } else if (stepY < 0) {
            tMaxY = (y - py) / dy;
        } else {
            tMaxY = Float.MAX_VALUE;
        }
        if (stepZ > 0) {
            tMaxZ = (z + 1 - pz) / dz;
        } else if (stepZ < 0) {
            tMaxZ = (z - pz) / dz;
        } else {
            tMaxZ = Float.MAX_VALUE;
        }

        int face = -1;
        float t = 0.0F;
        for (int i = 0; i < 128 && t <= (float) reach; i++) {
            if (level.isSolidTile(x, y, z)) {
                return face < 0 ? null : new HitResult(x, y, z, face);
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
                face = stepX > 0 ? 4 : 5;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
                face = stepY > 0 ? 0 : 1;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                face = stepZ > 0 ? 2 : 3;
            }
        }
        return null;
    }
}
