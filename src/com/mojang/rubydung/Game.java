package com.mojang.rubydung;

import com.mojang.rubydung.level.Level;
import com.mojang.rubydung.level.WorldRenderer;
import java.io.File;

/**
 * Owns the world, the player and the block under the active touch point.
 * The original used GL_SELECT for picking; this port uses a voxel raycast,
 * which is both API-1 friendly and exact on every GPU.
 */
public class Game {
    public static final int REACH = 5;
    /** Half of the vertical field of view used by Renderer.setupCamera(). */
    public static final float FOV_HALF_DEG = 35.0F;
    public static final float FOV_HALF_TAN =
            (float) Math.tan(FOV_HALF_DEG * Math.PI / 180.0);
    /** Renderer puts the camera this far behind the player's eye. */
    public static final float CAMERA_BACK = 0.3F;

    public final Level level;
    public final Player player;
    public final WorldRenderer world;
    public final InputState input = new InputState();
    /** Block under the finger (touch) or aiming direction (desktop/tests). */
    public HitResult hit;


    public Game(File filesDir) {
        this.level = new Level(256, 256, 64, filesDir);
        this.player = new Player(this.level);
        this.world = new WorldRenderer(this.level);
        this.updateHit();
    }

    public void tick() {
        this.player.tick(this.input);
    }

    public void updateWorld(long budgetNanos) {
        this.world.update(this.player, budgetNanos);
    }

    public void updateHit() {
        this.hit = this.raycast();
    }

    /** Pick through the finger position, matching MCPE freeform controls. */
    public void updateHitAt(float screenX, float screenY, int width, int height) {
        this.hit = this.raycastAt(screenX, screenY, width, height);
    }

    public void updateHitAt(float screenX, float screenY, int width, int height, float a) {
        this.hit = this.raycastAt(screenX, screenY, width, height, a);
    }

    public HitResult raycast() {
        return Raycaster.cast(this.level, this.player.x, this.player.y, this.player.z,
                this.player.yRot, this.player.xRot, REACH);
    }

    public HitResult raycastAt(float screenX, float screenY, int width, int height) {
        return this.raycastAt(screenX, screenY, width, height, 1.0F);
    }

    /**
     * @param a interpolation factor used by the renderer for the camera
     *          position; 1.0 means the current tick position.
     */
    public HitResult raycastAt(float screenX, float screenY, int width, int height, float a) {
        float ex = this.player.xo + (this.player.x - this.player.xo) * a;
        float ey = this.player.yo + (this.player.y - this.player.yo) * a;
        float ez = this.player.zo + (this.player.z - this.player.zo) * a;
        return Raycaster.castScreen(this.level, ex, ey, ez,
                this.player.yRot, this.player.xRot, screenX, screenY, width, height,
                FOV_HALF_TAN, CAMERA_BACK, REACH);
    }

    /** Same as the original left click: place on the face that was hit. */
    public void placeBlock() {
        if (this.hit == null) {
            return;
        }
        int x = this.hit.x;
        int y = this.hit.y;
        int z = this.hit.z;
        switch (this.hit.face) {
            case 0:
                y--;
                break;
            case 1:
                y++;
                break;
            case 2:
                z--;
                break;
            case 3:
                z++;
                break;
            case 4:
                x--;
                break;
            case 5:
                x++;
                break;
            default:
                return;
        }
        this.level.setTile(x, y, z, 1);
        // The caller refreshes hit for the current touch point; re-casting
        // from the screen centre here would move the highlight away from
        // the finger for one frame.
    }

    /** Same as the original right click: remove the targeted block. */
    public void breakBlock() {
        if (this.hit == null) {
            return;
        }
        this.level.setTile(this.hit.x, this.hit.y, this.hit.z, 0);
    }

    public void save() {
        this.level.save();
    }
}
