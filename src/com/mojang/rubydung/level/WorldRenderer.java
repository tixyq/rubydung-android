package com.mojang.rubydung.level;

import com.mojang.rubydung.Game;
import com.mojang.rubydung.Player;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import javax.microedition.khronos.opengles.GL10;

/**
 * Chunked world renderer for OpenGL ES 1.0.
 *
 * Chunks are client-side indexed triangle arrays. Rebuilds are scheduled per
 * frame with a time budget (not per simulation tick), so a slow frame can no
 * longer trigger a burst of chunk builds on the next one.
 */
public class WorldRenderer implements LevelListener {
    public static final int CHUNK = 16;
    public static final int VIEW_CHUNKS = 4;
    public static final int BUILD_CHUNKS = VIEW_CHUNKS;
    public static final float FOG_START = 40.0F;
    public static final float FOG_END = 64.0F;
    private static final int MAX_MESHES = 160;
    private static final int MAX_QUADS = MeshBuilder.MAX_VERTICES / 4;
    private static final float CHUNK_RADIUS_PAD = 1.0F;

    private static ShortBuffer indexBuffer;

    private final Level level;
    private final ChunkMesh[] chunks;
    private final int xChunks;
    private final int yChunks;
    private final int zChunks;
    private final MeshBuilder builder = new MeshBuilder();
    private final float[] planeNx = new float[5];
    private final float[] planeNy = new float[5];
    private final float[] planeNz = new float[5];
    private final float[] planeD = new float[5];
    private int meshCount;

    public WorldRenderer(Level level) {
        this.level = level;
        this.xChunks = level.width / CHUNK;
        this.yChunks = level.depth / CHUNK;
        this.zChunks = level.height / CHUNK;
        this.chunks = new ChunkMesh[this.xChunks * this.yChunks * this.zChunks];

        for (int x = 0; x < this.xChunks; x++) {
            for (int y = 0; y < this.yChunks; y++) {
                for (int z = 0; z < this.zChunks; z++) {
                    int x0 = x * CHUNK;
                    int y0 = y * CHUNK;
                    int z0 = z * CHUNK;
                    int x1 = x0 + CHUNK;
                    int y1 = y0 + CHUNK;
                    int z1 = z0 + CHUNK;
                    if (x1 > level.width) x1 = level.width;
                    if (y1 > level.depth) y1 = level.depth;
                    if (z1 > level.height) z1 = level.height;
                    this.chunks[this.index(x, y, z)] = new ChunkMesh(x0, y0, z0, x1, y1, z1);
                }
            }
        }
        level.addListener(this);
    }

    private static synchronized ShortBuffer indices() {
        if (indexBuffer == null) {
            ShortBuffer b = ByteBuffer.allocateDirect(MAX_QUADS * 6 * 2)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
            for (int q = 0; q < MAX_QUADS; q++) {
                int v = q * 4;
                b.put((short) v);
                b.put((short) (v + 1));
                b.put((short) (v + 2));
                b.put((short) v);
                b.put((short) (v + 2));
                b.put((short) (v + 3));
            }
            b.position(0);
            indexBuffer = b;
        }
        return indexBuffer;
    }

    private int index(int x, int y, int z) {
        return (y * this.xChunks + x) * this.zChunks + z;
    }

    public int meshCount() {
        return this.meshCount;
    }

    public int pendingBuilds(Player player) {
        int pcx = ((int) Math.floor(player.x)) >> 4;
        int pcz = ((int) Math.floor(player.z)) >> 4;
        int count = 0;
        for (int cx = pcx - BUILD_CHUNKS; cx <= pcx + BUILD_CHUNKS; cx++) {
            for (int cz = pcz - BUILD_CHUNKS; cz <= pcz + BUILD_CHUNKS; cz++) {
                for (int cy = 0; cy < this.yChunks; cy++) {
                    if (this.inRange(cx, cy, cz) && this.chunks[this.index(cx, cy, cz)].dirty) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public void allChanged() {
        for (int i = 0; i < this.chunks.length; i++) {
            this.chunks[i].dirty = true;
        }
    }

    public void tileChanged(int x, int y, int z) {
        int cx = x >> 4;
        int cy = y >> 4;
        int cz = z >> 4;
        this.markUrgent(cx, cy, cz);
        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;
        if (lx == 0) this.markUrgent(cx - 1, cy, cz);
        if (lx == 15) this.markUrgent(cx + 1, cy, cz);
        if (ly == 0) this.markUrgent(cx, cy - 1, cz);
        if (ly == 15) this.markUrgent(cx, cy + 1, cz);
        if (lz == 0) this.markUrgent(cx, cy, cz - 1);
        if (lz == 15) this.markUrgent(cx, cy, cz + 1);
    }

    public void lightChanged(int x, int z, int yFrom, int yTo) {
        int cy0 = Math.max(0, (yFrom - 1) >> 4);
        int cy1 = Math.min(this.yChunks - 1, (yTo + 1) >> 4);
        this.markColumn(x, z, cy0, cy1);
        this.markColumn(x - 1, z, cy0, cy1);
        this.markColumn(x + 1, z, cy0, cy1);
        this.markColumn(x, z - 1, cy0, cy1);
        this.markColumn(x, z + 1, cy0, cy1);
    }

    private void markColumn(int x, int z, int cy0, int cy1) {
        if (x < 0 || z < 0 || x >= this.level.width || z >= this.level.height) {
            return;
        }
        for (int cy = cy0; cy <= cy1; cy++) {
            this.markDirty(x >> 4, cy, z >> 4);
        }
    }

    private boolean inRange(int cx, int cy, int cz) {
        return cx >= 0 && cy >= 0 && cz >= 0
                && cx < this.xChunks && cy < this.yChunks && cz < this.zChunks;
    }

    private void markDirty(int cx, int cy, int cz) {
        if (this.inRange(cx, cy, cz)) {
            this.chunks[this.index(cx, cy, cz)].dirty = true;
        }
    }

    private void markUrgent(int cx, int cy, int cz) {
        if (this.inRange(cx, cy, cz)) {
            ChunkMesh mesh = this.chunks[this.index(cx, cy, cz)];
            mesh.dirty = true;
            mesh.urgent = true;
        }
    }

    private ChunkMesh findBest(int pcx, int pcz, Player player) {
        ChunkMesh best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int cx = pcx - BUILD_CHUNKS; cx <= pcx + BUILD_CHUNKS; cx++) {
            if (cx < 0 || cx >= this.xChunks) {
                continue;
            }
            for (int cz = pcz - BUILD_CHUNKS; cz <= pcz + BUILD_CHUNKS; cz++) {
                if (cz < 0 || cz >= this.zChunks) {
                    continue;
                }
                for (int cy = 0; cy < this.yChunks; cy++) {
                    ChunkMesh mesh = this.chunks[this.index(cx, cy, cz)];
                    if (!mesh.dirty) {
                        continue;
                    }
                    float dx = mesh.x0 + 8 - player.x;
                    float dz = mesh.z0 + 8 - player.z;
                    float distance = dx * dx + dz * dz;
                    if (mesh.urgent) {
                        distance -= 1000000.0F;
                    } else if (cy < 2) {
                        distance += 100000.0F;
                    }
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = mesh;
                    }
                }
            }
        }
        return best;
    }

    /** Builds dirty chunks, urgent ones first, until the time budget is used. */
    public void update(Player player, long budgetNanos) {
        long start = System.nanoTime();
        int pcx = ((int) Math.floor(player.x)) >> 4;
        int pcz = ((int) Math.floor(player.z)) >> 4;
        int built = 0;
        while (true) {
            ChunkMesh best = this.findBest(pcx, pcz, player);
            if (best == null) {
                break;
            }
            if (!best.urgent && built > 0 && System.nanoTime() - start >= budgetNanos) {
                break;
            }
            int before = best.vertexCount;
            best.build(this.level, this.builder);
            built++;
            if (before == 0 && best.vertexCount > 0) {
                this.meshCount++;
            } else if (before > 0 && best.vertexCount == 0) {
                this.meshCount--;
            }
        }
        if (this.meshCount > MAX_MESHES) {
            this.evict(player);
        }
    }

    public void tick(Player player) {
        this.update(player, 4000000L);
    }

    private void evict(Player player) {
        float buildRadius = BUILD_CHUNKS * CHUNK + 8.0F;
        float buildRadius2 = buildRadius * buildRadius;
        while (this.meshCount > MAX_MESHES) {
            ChunkMesh farthest = null;
            float farthestDistance = -1.0F;
            for (int i = 0; i < this.chunks.length; i++) {
                ChunkMesh mesh = this.chunks[i];
                if (mesh.vertexCount == 0) {
                    continue;
                }
                float dx = mesh.x0 + 8 - player.x;
                float dz = mesh.z0 + 8 - player.z;
                float distance = dx * dx + dz * dz;
                if (distance > farthestDistance) {
                    farthestDistance = distance;
                    farthest = mesh;
                }
            }
            if (farthest == null || farthestDistance < buildRadius2) {
                break;
            }
            farthest.free();
            this.meshCount--;
        }
    }

    private void setupFrustum(Player player, float a, float aspect) {
        float ex = player.xo + (player.x - player.xo) * a;
        float ey = player.yo + (player.y - player.yo) * a;
        float ez = player.zo + (player.z - player.zo) * a;
        float yaw = player.yRot * (float) Math.PI / 180.0F;
        float pitch = player.xRot * (float) Math.PI / 180.0F;
        float sy = (float) Math.sin(yaw);
        float cy = (float) Math.cos(yaw);
        float sp = (float) Math.sin(pitch);
        float cp = (float) Math.cos(pitch);

        float fx = sy * cp;
        float fy = -sp;
        float fz = -cy * cp;
        float rx = cy;
        float rz = sy;
        float ux = sp * sy;
        float uy = cp;
        float uz = -sp * cy;

        float camX = ex - fx * Game.CAMERA_BACK;
        float camY = ey - fy * Game.CAMERA_BACK;
        float camZ = ez - fz * Game.CAMERA_BACK;

        float t = Game.FOV_HALF_TAN;
        float s = t * aspect;

        this.planeNx[0] = rx + s * fx;
        this.planeNy[0] = s * fy;
        this.planeNz[0] = rz + s * fz;

        this.planeNx[1] = -rx + s * fx;
        this.planeNy[1] = s * fy;
        this.planeNz[1] = -rz + s * fz;

        this.planeNx[2] = ux + t * fx;
        this.planeNy[2] = uy + t * fy;
        this.planeNz[2] = uz + t * fz;

        this.planeNx[3] = -ux + t * fx;
        this.planeNy[3] = -uy + t * fy;
        this.planeNz[3] = -uz + t * fz;

        this.planeNx[4] = fx;
        this.planeNy[4] = fy;
        this.planeNz[4] = fz;

        for (int i = 0; i < 5; i++) {
            this.planeD[i] = this.planeNx[i] * camX + this.planeNy[i] * camY + this.planeNz[i] * camZ;
        }
    }

    private boolean visible(ChunkMesh mesh) {
        float minX = mesh.x0 - CHUNK_RADIUS_PAD;
        float maxX = mesh.x1 + CHUNK_RADIUS_PAD;
        float minY = mesh.minY - CHUNK_RADIUS_PAD;
        float maxY = mesh.maxY + CHUNK_RADIUS_PAD;
        float minZ = mesh.z0 - CHUNK_RADIUS_PAD;
        float maxZ = mesh.z1 + CHUNK_RADIUS_PAD;
        for (int i = 0; i < 5; i++) {
            float nx = this.planeNx[i];
            float ny = this.planeNy[i];
            float nz = this.planeNz[i];
            float px = nx >= 0.0F ? maxX : minX;
            float py = ny >= 0.0F ? maxY : minY;
            float pz = nz >= 0.0F ? maxZ : minZ;
            if (nx * px + ny * py + nz * pz < this.planeD[i]) {
                return false;
            }
        }
        return true;
    }

    public void render(GL10 gl, Player player, float a, float aspect) {
        float camX = player.xo + (player.x - player.xo) * a;
        float camZ = player.zo + (player.z - player.zo) * a;
        this.setupFrustum(player, a, aspect);

        float viewDistance = VIEW_CHUNKS * CHUNK + CHUNK;
        float viewDistance2 = viewDistance * viewDistance;
        ShortBuffer idx = indices();

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);

        for (int i = 0; i < this.chunks.length; i++) {
            ChunkMesh mesh = this.chunks[i];
            if (mesh.indexCount == 0) {
                continue;
            }
            float dx = mesh.x0 + 8 - camX;
            float dz = mesh.z0 + 8 - camZ;
            if (dx * dx + dz * dz > viewDistance2) {
                continue;
            }
            if (!this.visible(mesh)) {
                continue;
            }
            gl.glVertexPointer(3, GL10.GL_FLOAT, MeshBuilder.STRIDE, mesh.positions);
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, MeshBuilder.STRIDE, mesh.texCoords);
            gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, MeshBuilder.STRIDE, mesh.colours);
            gl.glDrawElements(GL10.GL_TRIANGLES, mesh.indexCount, GL10.GL_UNSIGNED_SHORT, idx);
        }

        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
    }
}
