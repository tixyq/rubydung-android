package com.mojang.rubydung;

import android.content.res.AssetManager;
import com.mojang.rubydung.level.WorldRenderer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ES 1.0 renderer. Everything here is core ES 1.0: client-side vertex
 * arrays, no display lists, no GL_QUADS and no GL_SELECT.
 */
public class Renderer {
    private static final float SKY_R = 0.5F;
    private static final float SKY_G = 0.8F;
    private static final float SKY_B = 1.0F;

    // MCPE 0.6.1 gui.png control tiles, at their original atlas positions.
    private static final int ATLAS = 256;
    private static final int ARROW_SRC = 26;
    private static final int UP_U = 0;
    private static final int UP_V = 107;
    private static final int UPLEFT_U = 0;
    private static final int UPLEFT_V = 133;
    private static final int UPRIGHT_U = 26;
    private static final int UPRIGHT_V = 133;
    private static final int LEFT_U = 26;
    private static final int LEFT_V = 107;
    private static final int RIGHT_U = 78;
    private static final int RIGHT_V = 107;
    private static final int DOWN_U = 52;
    private static final int DOWN_V = 107;
    private static final int JUMP_U = 104;
    private static final int JUMP_V = 107;

    // MCPE touch feedback ring: 24 segments, two triangles per segment.
    private static final int FEEDBACK_SEGMENTS = 24;
    private static final int FEEDBACK_VERTS = FEEDBACK_SEGMENTS * 6;

    private final AssetManager assets;
    private final FloatBuffer quadPos;
    private final FloatBuffer quadUv;
    private final FloatBuffer worldPos;
    private final FloatBuffer ringPos;

    private final FloatBuffer fogColor;

    private int width;
    private int height;
    private int terrainTexture;
    private int controlsTexture;
    private boolean softwareGl;
    private float eyeX;
    private float eyeY;
    private float eyeZ;

    public Renderer(AssetManager assets) {
        this.assets = assets;
        this.quadPos = directFloats(6 * 2);
        this.quadUv = directFloats(6 * 2);
        this.worldPos = directFloats(24 * 3);
        this.ringPos = directFloats(FEEDBACK_VERTS * 2);
        this.fogColor = directFloats(4);
        this.fogColor.put(SKY_R).put(SKY_G).put(SKY_B).put(1.0F);
        this.fogColor.position(0);
    }

    private static FloatBuffer directFloats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public void onSurfaceCreated(GL10 gl, int width, int height) {
        this.width = width;
        this.height = height;

        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glShadeModel(GL10.GL_SMOOTH);
        gl.glClearColor(SKY_R, SKY_G, SKY_B, 1.0F);
        gl.glClearDepthf(1.0F);
        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glDepthFunc(GL10.GL_LEQUAL);
        gl.glEnable(GL10.GL_CULL_FACE);
        gl.glCullFace(GL10.GL_BACK);
        gl.glFrontFace(GL10.GL_CCW);
        gl.glDisable(GL10.GL_BLEND);
        gl.glDisable(GL10.GL_ALPHA_TEST);
        gl.glTexEnvx(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);

        String vendorRenderer = null;
        try {
            vendorRenderer = gl.glGetString(GL10.GL_RENDERER);
        } catch (Throwable ignored) {
        }
        this.softwareGl = vendorRenderer != null
                && vendorRenderer.indexOf("PixelFlinger") >= 0;
        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT,
                this.softwareGl ? GL10.GL_FASTEST : GL10.GL_NICEST);

        gl.glFogf(GL10.GL_FOG_MODE, GL10.GL_LINEAR);
        gl.glFogf(GL10.GL_FOG_START, WorldRenderer.FOG_START);
        gl.glFogf(GL10.GL_FOG_END, WorldRenderer.FOG_END);
        gl.glFogfv(GL10.GL_FOG_COLOR, this.fogColor);

        this.terrainTexture = Textures.load(gl, this.assets, "terrain.png", false, !this.softwareGl);
        this.controlsTexture = Textures.load(gl, this.assets, "controls.png", false, false);
        gl.glEnable(GL10.GL_TEXTURE_2D);
    }

    public void onSizeChanged(int width, int height) {
        this.width = width;
        this.height = height;
        // The GL object is used inside render(), so the viewport is set there.
    }

    /**
     * @param calibration -1 normally; 0..1 while the trackball calibration
     *                    hint (arrow pointing right + progress bar) is shown
     */
    public void render(GL10 gl, Game game, TouchControls controls, float a,
                       float calibration) {
        if (this.width <= 0 || this.height <= 0) {
            return;
        }

        gl.glViewport(0, 0, this.width, this.height);
        gl.glClearColor(SKY_R, SKY_G, SKY_B, 1.0F);
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glEnable(GL10.GL_CULL_FACE);
        gl.glCullFace(GL10.GL_BACK);
        gl.glDisable(GL10.GL_BLEND);
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, this.terrainTexture);
        gl.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        this.setupCamera(gl, game.player, a);

        gl.glEnable(GL10.GL_FOG);
        game.world.render(gl, game.player, a, (float) this.width / (float) this.height);
        gl.glDisable(GL10.GL_FOG);

        gl.glDisable(GL10.GL_TEXTURE_2D);
        this.drawHighlight(gl, game.hit);

        this.setupUi(gl);
        if (controls.visible) {
            this.drawTouchFeedback(gl, controls, game.hit != null);
            this.drawControls(gl, controls);
        }
        if (calibration >= 0.0F) {
            this.drawCalibration(gl, calibration);
        }

        gl.glDisable(GL10.GL_BLEND);
        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glEnable(GL10.GL_CULL_FACE);
        gl.glEnable(GL10.GL_TEXTURE_2D);
    }

    private void setupCamera(GL10 gl, Player player, float a) {
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        float aspect = (float) this.width / (float) this.height;
        float near = 0.1F;
        float far = 256.0F;
        float top = near * Game.FOV_HALF_TAN;
        float right = top * aspect;
        gl.glFrustumf(-right, right, -top, top, near, far);

        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glTranslatef(0.0F, 0.0F, -Game.CAMERA_BACK);
        gl.glRotatef(player.xRot, 1.0F, 0.0F, 0.0F);
        gl.glRotatef(player.yRot, 0.0F, 1.0F, 0.0F);
        float x = player.xo + (player.x - player.xo) * a;
        float y = player.yo + (player.y - player.yo) * a;
        float z = player.zo + (player.z - player.zo) * a;
        this.eyeX = x;
        this.eyeY = y;
        this.eyeZ = z;
        gl.glTranslatef(-x, -y, -z);
    }

    private void setupUi(GL10 gl) {
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        // y grows downwards so touch coordinates map directly.
        gl.glOrthof(0.0F, this.width, this.height, 0.0F, -1.0F, 1.0F);
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glDisable(GL10.GL_DEPTH_TEST);
        gl.glDisable(GL10.GL_CULL_FACE);
        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL10.GL_TEXTURE_2D);
    }

    private void drawHighlight(GL10 gl, HitResult hit) {
        if (hit == null) {
            return;
        }
        gl.glDisable(GL10.GL_TEXTURE_2D);
        gl.glDisable(GL10.GL_CULL_FACE);
        gl.glEnable(GL10.GL_BLEND);
        gl.glDepthMask(false);

        float ddx = hit.x + 0.5F - this.eyeX;
        float ddy = hit.y + 0.5F - this.eyeY;
        float ddz = hit.z + 0.5F - this.eyeZ;
        float dist2 = ddx * ddx + ddy * ddy + ddz * ddz;
        float off = 0.004F + dist2 / 2500.0F;

        float pulse = 0.28F + 0.14F * (float) Math.sin(System.nanoTime() / 150000000.0);
        this.fillFace(hit, off);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, this.worldPos);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE);
        gl.glColor4f(1.0F, 1.0F, 1.0F, pulse);
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 6);

        this.fillCubeEdges(hit, off + 0.002F);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, this.worldPos);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glColor4f(0.0F, 0.0F, 0.0F, 0.35F);
        gl.glLineWidth(Math.min(3.0F, Math.max(1.0F, this.height / 480.0F * 2.0F)));
        gl.glDrawArrays(GL10.GL_LINES, 0, 24);

        gl.glDepthMask(true);
        gl.glDisable(GL10.GL_BLEND);
        gl.glEnable(GL10.GL_CULL_FACE);
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void fillFace(HitResult hit, float off) {
        float x0 = hit.x;
        float y0 = hit.y;
        float z0 = hit.z;
        float x1 = x0 + 1.0F;
        float y1 = y0 + 1.0F;
        float z1 = z0 + 1.0F;
        float ox = 0.0F;
        float oy = 0.0F;
        float oz = 0.0F;
        if (hit.face == 0) oy = -off;
        if (hit.face == 1) oy = off;
        if (hit.face == 2) oz = -off;
        if (hit.face == 3) oz = off;
        if (hit.face == 4) ox = -off;
        if (hit.face == 5) ox = off;
        x0 += ox; x1 += ox;
        y0 += oy; y1 += oy;
        z0 += oz; z1 += oz;

        this.worldPos.clear();
        switch (hit.face) {
            case 0:
                put3(this.worldPos, x0, y0, z1);
                put3(this.worldPos, x0, y0, z0);
                put3(this.worldPos, x1, y0, z0);
                put3(this.worldPos, x0, y0, z1);
                put3(this.worldPos, x1, y0, z0);
                put3(this.worldPos, x1, y0, z1);
                break;
            case 1:
                put3(this.worldPos, x1, y1, z1);
                put3(this.worldPos, x1, y1, z0);
                put3(this.worldPos, x0, y1, z0);
                put3(this.worldPos, x1, y1, z1);
                put3(this.worldPos, x0, y1, z0);
                put3(this.worldPos, x0, y1, z1);
                break;
            case 2:
                put3(this.worldPos, x0, y1, z0);
                put3(this.worldPos, x1, y1, z0);
                put3(this.worldPos, x1, y0, z0);
                put3(this.worldPos, x0, y1, z0);
                put3(this.worldPos, x1, y0, z0);
                put3(this.worldPos, x0, y0, z0);
                break;
            case 3:
                put3(this.worldPos, x0, y1, z1);
                put3(this.worldPos, x0, y0, z1);
                put3(this.worldPos, x1, y0, z1);
                put3(this.worldPos, x0, y1, z1);
                put3(this.worldPos, x1, y0, z1);
                put3(this.worldPos, x1, y1, z1);
                break;
            case 4:
                put3(this.worldPos, x0, y1, z1);
                put3(this.worldPos, x0, y1, z0);
                put3(this.worldPos, x0, y0, z0);
                put3(this.worldPos, x0, y1, z1);
                put3(this.worldPos, x0, y0, z0);
                put3(this.worldPos, x0, y0, z1);
                break;
            default:
                put3(this.worldPos, x1, y0, z1);
                put3(this.worldPos, x1, y0, z0);
                put3(this.worldPos, x1, y1, z0);
                put3(this.worldPos, x1, y0, z1);
                put3(this.worldPos, x1, y1, z0);
                put3(this.worldPos, x1, y1, z1);
                break;
        }
        this.worldPos.flip();
    }

    private void fillCubeEdges(HitResult hit, float off) {
        float x0 = hit.x - off;
        float y0 = hit.y - off;
        float z0 = hit.z - off;
        float x1 = hit.x + 1.0F + off;
        float y1 = hit.y + 1.0F + off;
        float z1 = hit.z + 1.0F + off;
        this.worldPos.clear();
        // bottom
        put3(this.worldPos, x0, y0, z0); put3(this.worldPos, x1, y0, z0);
        put3(this.worldPos, x1, y0, z0); put3(this.worldPos, x1, y0, z1);
        put3(this.worldPos, x1, y0, z1); put3(this.worldPos, x0, y0, z1);
        put3(this.worldPos, x0, y0, z1); put3(this.worldPos, x0, y0, z0);
        // top
        put3(this.worldPos, x0, y1, z0); put3(this.worldPos, x1, y1, z0);
        put3(this.worldPos, x1, y1, z0); put3(this.worldPos, x1, y1, z1);
        put3(this.worldPos, x1, y1, z1); put3(this.worldPos, x0, y1, z1);
        put3(this.worldPos, x0, y1, z1); put3(this.worldPos, x0, y1, z0);
        // vertical
        put3(this.worldPos, x0, y0, z0); put3(this.worldPos, x0, y1, z0);
        put3(this.worldPos, x1, y0, z0); put3(this.worldPos, x1, y1, z0);
        put3(this.worldPos, x1, y0, z1); put3(this.worldPos, x1, y1, z1);
        put3(this.worldPos, x0, y0, z1); put3(this.worldPos, x0, y1, z1);
        this.worldPos.flip();
    }

    private static void put3(FloatBuffer b, float x, float y, float z) {
        b.put(x);
        b.put(y);
        b.put(z);
    }

    /**
     * MCPE 0.6.1 touch feedback ring, ported from Gui::renderProgressIndicator():
     * on the touch interface there is no centre crosshair; a white ring appears
     * under the look finger, grows while the player holds still, and reaches
     * full opacity when the hold turns into block destruction.
     */
    private void drawTouchFeedback(GL10 gl, TouchControls c, boolean hasHit) {
        if (!c.feedbackActive || c.breakProgress <= 0.0F) {
            return;
        }
        float progress = Math.min(1.0F, c.breakProgress);
        float fade = progress * progress; // MCPE squares the hold progress.
        // MCPE: 0.8 alpha when aiming at a block, otherwise a faint 0.4.
        float alpha = hasHit ? 0.8F * fade : Math.min(0.4F, 0.4F * fade);
        if (alpha < 0.02F) {
            return;
        }

        // Same proportions as MCPE's 80 px ring at GUI scale 1 (480 px tall).
        float radius = 40.0F * Math.min(this.width, this.height) / 480.0F;
        if (radius < 16.0F) {
            radius = 16.0F;
        }
        if (radius > 140.0F) {
            radius = 140.0F;
        }
        float thickness = Math.max(2.0F, radius * 0.05F);
        this.buildFeedbackRing(c.feedbackX, c.feedbackY, radius - thickness, radius);

        gl.glDisable(GL10.GL_TEXTURE_2D);
        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glColor4f(1.0F, 1.0F, 1.0F, alpha);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, this.ringPos);
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, FEEDBACK_VERTS);
        gl.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void buildFeedbackRing(float cx, float cy, float inner, float outer) {
        this.ringPos.clear();
        for (int i = 0; i < FEEDBACK_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / FEEDBACK_SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / FEEDBACK_SEGMENTS;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);

            float ox0 = cx + c0 * outer;
            float oy0 = cy + s0 * outer;
            float ix0 = cx + c0 * inner;
            float iy0 = cy + s0 * inner;
            float ox1 = cx + c1 * outer;
            float oy1 = cy + s1 * outer;
            float ix1 = cx + c1 * inner;
            float iy1 = cy + s1 * inner;

            put2(this.ringPos, ox0, oy0);
            put2(this.ringPos, ix0, iy0);
            put2(this.ringPos, ix1, iy1);

            put2(this.ringPos, ox0, oy0);
            put2(this.ringPos, ix1, iy1);
            put2(this.ringPos, ox1, oy1);
        }
        this.ringPos.flip();
    }

    /**
     * Trackball calibration hint, no text so it needs no font: the screen is
     * dimmed, a big arrow points to the right and a bar fills as the player
     * rolls the ball that way.
     */
    private void drawCalibration(GL10 gl, float progress) {
        float w = this.width;
        float h = this.height;
        this.solidQuad(gl, 0.0F, 0.0F, w, h, 0.0F, 0.0F, 0.0F, 0.65F);

        float size = Math.min(w, h) * 0.4F;
        float cx = w * 0.5F;
        float cy = h * 0.45F;
        gl.glBindTexture(GL10.GL_TEXTURE_2D, this.controlsTexture);
        this.controlTile(gl, cx - size * 0.5F, cy - size * 0.5F, size,
                RIGHT_U, RIGHT_V, ARROW_SRC, 1.0F);

        float barW = Math.min(w, h) * 0.8F;
        float barH = Math.max(6.0F, Math.min(w, h) * 0.03F);
        float bx = cx - barW * 0.5F;
        float by = cy + size * 0.5F + barH * 2.0F;
        this.solidQuad(gl, bx, by, bx + barW, by + barH, 1.0F, 1.0F, 1.0F, 0.25F);
        this.solidQuad(gl, bx, by, bx + barW * progress, by + barH, 1.0F, 1.0F, 1.0F, 0.95F);
        gl.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void solidQuad(GL10 gl, float x0, float y0, float x1, float y1,
                           float r, float g, float b, float alpha) {
        this.quadPos.clear();
        put2(this.quadPos, x0, y0);
        put2(this.quadPos, x1, y0);
        put2(this.quadPos, x1, y1);
        put2(this.quadPos, x0, y0);
        put2(this.quadPos, x1, y1);
        put2(this.quadPos, x0, y1);
        this.quadPos.flip();
        gl.glDisable(GL10.GL_TEXTURE_2D);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glColor4f(r, g, b, alpha);
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, this.quadPos);
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 6);
        gl.glEnable(GL10.GL_TEXTURE_2D);
    }

    private void drawControls(GL10 gl, TouchControls c) {
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, this.controlsTexture);

        boolean n = c.pressCount[TouchControls.N] > 0
                || c.pressCount[TouchControls.NW] > 0
                || c.pressCount[TouchControls.NE] > 0;
        boolean s = c.pressCount[TouchControls.S] > 0
                || c.pressCount[TouchControls.SW] > 0
                || c.pressCount[TouchControls.SE] > 0;
        boolean w = c.pressCount[TouchControls.W] > 0
                || c.pressCount[TouchControls.NW] > 0
                || c.pressCount[TouchControls.SW] > 0;
        boolean e = c.pressCount[TouchControls.E] > 0
                || c.pressCount[TouchControls.NE] > 0
                || c.pressCount[TouchControls.SE] > 0;
        boolean nw = c.pressCount[TouchControls.NW] > 0;
        boolean ne = c.pressCount[TouchControls.NE] > 0;
        boolean jump = c.pressCount[TouchControls.JUMP] > 0;

        // Faint diagonal hints, as in MCPE.
        this.controlTile(gl, c.padX, c.padY, c.btn, UPLEFT_U, UPLEFT_V, ARROW_SRC,
                nw ? 0.9F : 0.16F);
        this.controlTile(gl, c.padX + 2.0F * c.btn, c.padY, c.btn, UPRIGHT_U, UPRIGHT_V, ARROW_SRC,
                ne ? 0.9F : 0.16F);

        this.controlTile(gl, c.padX + c.btn, c.padY, c.btn, UP_U, UP_V, ARROW_SRC,
                n ? 0.9F : 0.45F);
        this.controlTile(gl, c.padX, c.padY + c.btn, c.btn, LEFT_U, LEFT_V, ARROW_SRC,
                w ? 0.9F : 0.45F);
        this.controlTile(gl, c.padX + 2.0F * c.btn, c.padY + c.btn, c.btn, RIGHT_U, RIGHT_V, ARROW_SRC,
                e ? 0.9F : 0.45F);
        this.controlTile(gl, c.padX + c.btn, c.padY + 2.0F * c.btn, c.btn, DOWN_U, DOWN_V, ARROW_SRC,
                s ? 0.9F : 0.45F);
        this.controlTile(gl, c.padX + c.btn, c.padY + c.btn, c.btn, JUMP_U, JUMP_V, ARROW_SRC,
                jump ? 0.9F : 0.5F);
    }

    private void controlTile(GL10 gl, float x, float y, float size, int u, int v, int srcSize, float alpha) {
        gl.glColor4f(1.0F, 1.0F, 1.0F, alpha);
        float u0 = (float) u / ATLAS;
        float v0 = (float) v / ATLAS;
        float u1 = (float) (u + srcSize) / ATLAS;
        float v1 = (float) (v + srcSize) / ATLAS;
        this.texturedQuad(gl, x, y, x + size, y + size, u0, v0, u1, v1);
    }

    private void texturedQuad(GL10 gl, float x0, float y0, float x1, float y1,
                              float u0, float v0, float u1, float v1) {
        this.quadPos.clear();
        put2(this.quadPos, x0, y0);
        put2(this.quadPos, x1, y0);
        put2(this.quadPos, x1, y1);
        put2(this.quadPos, x0, y0);
        put2(this.quadPos, x1, y1);
        put2(this.quadPos, x0, y1);
        this.quadPos.flip();

        this.quadUv.clear();
        put2(this.quadUv, u0, v0);
        put2(this.quadUv, u1, v0);
        put2(this.quadUv, u1, v1);
        put2(this.quadUv, u0, v0);
        put2(this.quadUv, u1, v1);
        put2(this.quadUv, u0, v1);
        this.quadUv.flip();

        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, this.quadPos);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, this.quadUv);
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 6);
    }

    private static void put2(FloatBuffer b, float x, float y) {
        b.put(x);
        b.put(y);
    }
}
