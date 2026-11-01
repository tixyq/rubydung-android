package com.mojang.rubydung.level;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Collects the vertices of one chunk. Four vertices per quad, interleaved:
 *   position (3 floats, offset 0)
 *   texture  (2 floats, offset 12)
 *   colour   (4 unsigned bytes, offset 20)
 *   stride = 24 bytes
 *
 * One instance is reused for every chunk build, so building a chunk does not
 * allocate. Triangles come from a shared index buffer (0,1,2, 0,2,3 per quad).
 */
public class MeshBuilder {
    public static final int STRIDE = 24;
    public static final int MAX_VERTICES = 65532;

    private ByteBuffer buf;
    private int vertices;

    public MeshBuilder() {
        this.buf = ByteBuffer.allocateDirect(96 * 1024).order(ByteOrder.nativeOrder());
    }

    public void reset() {
        this.buf.clear();
        this.vertices = 0;
    }

    private void ensure(int bytes) {
        if (this.buf.remaining() >= bytes) {
            return;
        }
        int capacity = this.buf.capacity() * 2;
        while (capacity < this.buf.position() + bytes) {
            capacity *= 2;
        }
        ByteBuffer next = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
        this.buf.flip();
        next.put(this.buf);
        this.buf = next;
    }

    private void vertex(float x, float y, float z, float u, float v, int rgba) {
        ByteBuffer b = this.buf;
        b.putFloat(x);
        b.putFloat(y);
        b.putFloat(z);
        b.putFloat(u);
        b.putFloat(v);
        b.putInt(rgba);
    }

    public boolean quad(
            float ax, float ay, float az, float au, float av,
            float bx, float by, float bz, float bu, float bv,
            float cx, float cy, float cz, float cu, float cv,
            float dx, float dy, float dz, float du, float dv,
            float shade) {
        if (this.vertices + 4 > MAX_VERTICES) {
            return false;
        }
        this.ensure(4 * STRIDE);
        int c = (int) (shade * 255.0F + 0.5F);
        if (c < 0) {
            c = 0;
        }
        if (c > 255) {
            c = 255;
        }
        int rgba = c | (c << 8) | (c << 16) | 0xFF000000;
        this.vertex(ax, ay, az, au, av, rgba);
        this.vertex(bx, by, bz, bu, bv, rgba);
        this.vertex(cx, cy, cz, cu, cv, rgba);
        this.vertex(dx, dy, dz, du, dv, rgba);
        this.vertices += 4;
        return true;
    }

    public int vertexCount() {
        return this.vertices;
    }

    public int quadCount() {
        return this.vertices >> 2;
    }

    public ByteBuffer buffer() {
        return this.buf;
    }
}
