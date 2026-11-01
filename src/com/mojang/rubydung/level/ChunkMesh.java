package com.mojang.rubydung.level;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One 16x16x16 piece of the world, stored as an interleaved vertex block that
 * is drawn with glDrawElements. The vertex block is reused between rebuilds
 * whenever it is still big enough, so editing blocks does not churn memory.
 */
public class ChunkMesh {
    public final int x0;
    public final int y0;
    public final int z0;
    public final int x1;
    public final int y1;
    public final int z1;

    public boolean dirty = true;
    public boolean urgent;
    public int vertexCount;
    public int indexCount;
    public float minY;
    public float maxY;
    public ByteBuffer data;
    public ByteBuffer positions;
    public ByteBuffer texCoords;
    public ByteBuffer colours;

    public ChunkMesh(int x0, int y0, int z0, int x1, int y1, int z1) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
    }

    public void build(Level level) {
        this.build(level, new MeshBuilder());
    }

    public void build(Level level, MeshBuilder builder) {
        this.dirty = false;
        this.urgent = false;
        builder.reset();

        byte[] blocks = level.getBlocks();
        int w = level.width;
        int h = level.height;
        int d = level.depth;
        int layer = w * h;
        int surface = d * 2 / 3;
        int lowY = Integer.MAX_VALUE;
        int highY = Integer.MIN_VALUE;

        for (int y = this.y0; y < this.y1; y++) {
            int tex = (y == surface) ? 0 : 1;
            int before = builder.vertexCount();
            for (int z = this.z0; z < this.z1; z++) {
                int i = (y * h + z) * w + this.x0;
                for (int x = this.x0; x < this.x1; x++, i++) {
                    if (blocks[i] != 1) {
                        continue;
                    }
                    if (y > 0 && blocks[i - layer] != 1) {
                        Tile.renderFace(builder, level, x, y, z, Tile.FACE_BOTTOM, tex);
                    }
                    if (y + 1 >= d || blocks[i + layer] != 1) {
                        Tile.renderFace(builder, level, x, y, z, Tile.FACE_TOP, tex);
                    }
                    if (z == 0 || blocks[i - w] != 1) {
                        Tile.renderFace(builder, level, x, y, z, Tile.FACE_NORTH, tex);
                    }
                    if (z + 1 >= h || blocks[i + w] != 1) {
                        Tile.renderFace(builder, level, x, y, z, Tile.FACE_SOUTH, tex);
                    }
                    if (x == 0 || blocks[i - 1] != 1) {
                        Tile.renderFace(builder, level, x, y, z, Tile.FACE_WEST, tex);
                    }
                    if (x + 1 >= w || blocks[i + 1] != 1) {
                        Tile.renderFace(builder, level, x, y, z, Tile.FACE_EAST, tex);
                    }
                }
            }
            if (builder.vertexCount() != before) {
                if (y < lowY) {
                    lowY = y;
                }
                highY = y + 1;
            }
        }

        this.vertexCount = builder.vertexCount();
        this.indexCount = builder.quadCount() * 6;
        if (this.vertexCount == 0) {
            this.release();
            return;
        }
        this.minY = lowY;
        this.maxY = highY;

        int bytes = this.vertexCount * MeshBuilder.STRIDE;
        if (this.data == null || this.data.capacity() < bytes || this.data.capacity() > bytes + 32768) {
            int capacity = (bytes + 4095) & ~4095;
            this.data = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
            this.positions = this.data.duplicate();
            this.texCoords = this.data.duplicate();
            this.colours = this.data.duplicate();
        }
        ByteBuffer src = builder.buffer();
        src.flip();
        this.data.clear();
        this.data.put(src);
        src.clear();

        this.positions.clear();
        this.positions.limit(bytes);
        this.texCoords.clear();
        this.texCoords.limit(bytes);
        this.texCoords.position(12);
        this.colours.clear();
        this.colours.limit(bytes);
        this.colours.position(20);
    }

    private void release() {
        this.data = null;
        this.positions = null;
        this.texCoords = null;
        this.colours = null;
        this.indexCount = 0;
    }

    public void free() {
        this.vertexCount = 0;
        this.release();
        this.dirty = true;
    }
}
