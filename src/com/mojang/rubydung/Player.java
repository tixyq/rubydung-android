package com.mojang.rubydung;

import com.mojang.rubydung.level.Level;
import com.mojang.rubydung.phys.AABB;
import java.util.List;

/**
 * The original rd-132211 player: same hitbox, gravity, jump impulse and
 * friction. Keyboard polling has been replaced by an InputState.
 */
public class Player {
    private Level level;
    public float xo;
    public float yo;
    public float zo;
    public float x;
    public float y;
    public float z;
    public float xd;
    public float yd;
    public float zd;
    public float yRot;
    public float xRot;
    public AABB bb;
    private final AABB sweep = new AABB(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    public boolean onGround = false;

    public Player(Level level) {
        this.level = level;
        this.resetPos();
    }

    public void resetPos() {
        float x = (float) Math.random() * this.level.width;
        float y = this.level.depth + 10;
        float z = (float) Math.random() * this.level.height;
        this.setPos(x, y, z);
        this.xd = 0.0F;
        this.yd = 0.0F;
        this.zd = 0.0F;
        this.xRot = 0.0F;
    }

    private void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.xo = x;
        this.yo = y;
        this.zo = z;
        float w = 0.3F;
        float h = 0.9F;
        this.bb = new AABB(x - w, y - h, z - w, x + w, y + h, z + w);
    }

    /** Used by tests and by the automatic void respawn path. */
    public void setPosition(float x, float y, float z) {
        this.setPos(x, y, z);
        this.xd = 0.0F;
        this.yd = 0.0F;
        this.zd = 0.0F;
        this.onGround = false;
    }

    /** Touch/desktop look, already in degrees. */
    public void turn(float dx, float dy) {
        this.yRot += dx;
        this.xRot += dy;
        if (this.xRot < -90.0F) {
            this.xRot = -90.0F;
        }
        if (this.xRot > 90.0F) {
            this.xRot = 90.0F;
        }
        if (this.yRot > 180.0F) {
            this.yRot -= 360.0F;
        }
        if (this.yRot < -180.0F) {
            this.yRot += 360.0F;
        }
    }

    public void tick(InputState input) {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        float xa = 0.0F;
        float ya = 0.0F;
        if (input.forward) {
            ya--;
        }
        if (input.back) {
            ya++;
        }
        if (input.left) {
            xa--;
        }
        if (input.right) {
            xa++;
        }
        if (input.jump && this.onGround) {
            this.yd = 0.12F;
        }
        this.moveRelative(xa, ya, this.onGround ? 0.02F : 0.005F);
        this.yd = this.yd - 0.005F;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.91F;
        this.yd *= 0.98F;
        this.zd *= 0.91F;
        if (this.onGround) {
            this.xd *= 0.8F;
            this.zd *= 0.8F;
        }
        // The desktop original only respawned on the R key. On a phone there
        // is no R, so falling out of the world respawns automatically.
        if (this.y < -24.0F) {
            this.resetPos();
        }
    }

    public void move(float xa, float ya, float za) {
        float xaOrg = xa;
        float yaOrg = ya;
        float zaOrg = za;
        this.bb.expandInto(this.sweep, xa, ya, za);
        List<AABB> boxes = this.level.getCubes(this.sweep);
        for (int i = 0; i < boxes.size(); i++) {
            ya = boxes.get(i).clipYCollide(this.bb, ya);
        }
        this.bb.move(0.0F, ya, 0.0F);
        for (int i = 0; i < boxes.size(); i++) {
            xa = boxes.get(i).clipXCollide(this.bb, xa);
        }
        this.bb.move(xa, 0.0F, 0.0F);
        for (int i = 0; i < boxes.size(); i++) {
            za = boxes.get(i).clipZCollide(this.bb, za);
        }
        this.bb.move(0.0F, 0.0F, za);
        this.onGround = yaOrg != ya && yaOrg < 0.0F;
        if (xaOrg != xa) {
            this.xd = 0.0F;
        }
        if (yaOrg != ya) {
            this.yd = 0.0F;
        }
        if (zaOrg != za) {
            this.zd = 0.0F;
        }
        this.x = (this.bb.x0 + this.bb.x1) / 2.0F;
        this.y = this.bb.y0 + 1.62F;
        this.z = (this.bb.z0 + this.bb.z1) / 2.0F;
    }

    public void moveRelative(float xa, float za, float speed) {
        float dist = xa * xa + za * za;
        if (dist < 0.01F) {
            return;
        }
        dist = speed / (float) Math.sqrt(dist);
        xa *= dist;
        za *= dist;
        float sin = (float) Math.sin(this.yRot * Math.PI / 180.0);
        float cos = (float) Math.cos(this.yRot * Math.PI / 180.0);
        this.xd += xa * cos - za * sin;
        this.zd += za * cos + xa * sin;
    }
}
