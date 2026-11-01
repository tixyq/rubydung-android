package com.mojang.rubydung.level;

import com.mojang.rubydung.phys.AABB;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Same world as rd-132211: 256 x 64 x 256, flat, layer 42 is the grass surface.
 * The save file is byte-for-byte the original format (gzip of the raw block
 * array, no header), so a level.dat from the desktop version also loads here.
 */
public class Level {
    public final int width;
    public final int height;
    public final int depth;
    private byte[] blocks;
    private int[] lightDepths;
    private final File saveFile;
    private final Object saveLock = new Object();
    private boolean dirty;
    private final ArrayList<LevelListener> levelListeners = new ArrayList<LevelListener>();
    private final ArrayList<AABB> cubeList = new ArrayList<AABB>();
    private AABB[] cubePool = new AABB[64];
    private int cubePoolUsed;

    public Level(int w, int h, int d, File dir) {
        this.width = w;
        this.height = h;
        this.depth = d;
        this.blocks = new byte[w * h * d];
        this.lightDepths = new int[w * h];
        this.saveFile = dir == null ? new File("level.dat") : new File(dir, "level.dat");

        int layer = w * h;
        int top = d * 2 / 3;
        for (int y = 0; y <= top && y < d; y++) {
            Arrays.fill(this.blocks, y * layer, (y + 1) * layer, (byte) 1);
        }
        this.calcLightDepths(0, 0, w, h);
        this.load();
    }

    public void load() {
        if (!this.saveFile.exists()) {
            this.dirty = false;
            return;
        }
        try {
            byte[] tmp = new byte[this.blocks.length];
            DataInputStream dis = new DataInputStream(
                    new GZIPInputStream(new FileInputStream(this.saveFile)));
            dis.readFully(tmp);
            dis.close();
            this.blocks = tmp;
            this.calcLightDepths(0, 0, this.width, this.height);
            this.dirty = false;
            this.allChanged();
        } catch (Exception e) {
            // Corrupt or foreign save: keep the freshly generated world and
            // persist that instead of leaving the bad file in place.
            this.dirty = true;
            e.printStackTrace();
        }
    }

    public void save() {
        synchronized (this.saveLock) {
            if (!this.dirty && this.saveFile.exists()) {
                return;
            }
            File tmp = new File(this.saveFile.getAbsolutePath() + ".tmp");
            try {
                DataOutputStream dos = new DataOutputStream(
                        new GZIPOutputStream(new FileOutputStream(tmp)));
                dos.write(this.blocks);
                dos.close();
                if (this.saveFile.exists()) {
                    this.saveFile.delete();
                }
                if (!tmp.renameTo(this.saveFile)) {
                    // Some old filesystems refuse rename over an existing file.
                    DataOutputStream out = new DataOutputStream(
                            new GZIPOutputStream(new FileOutputStream(this.saveFile)));
                    out.write(this.blocks);
                    out.close();
                    tmp.delete();
                }
                this.dirty = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void calcLightDepths(int x0, int y0, int x1, int y1) {
        byte[] b = this.blocks;
        int w = this.width;
        int layer = this.width * this.height;
        for (int x = x0; x < x0 + x1; x++) {
            for (int z = y0; z < y0 + y1; z++) {
                if (x < 0 || z < 0 || x >= this.width || z >= this.height) {
                    continue;
                }
                int y = this.depth - 1;
                int i = (y * this.height + z) * w + x;
                while (y > 0 && b[i] != 1) {
                    y--;
                    i -= layer;
                }
                this.lightDepths[x + z * w] = y;
            }
        }
    }

    public byte[] getBlocks() {
        return this.blocks;
    }

    public void addListener(LevelListener listener) {
        this.levelListeners.add(listener);
    }

    public void removeListener(LevelListener listener) {
        this.levelListeners.remove(listener);
    }

    private void allChanged() {
        for (int i = 0; i < this.levelListeners.size(); i++) {
            this.levelListeners.get(i).allChanged();
        }
    }

    public boolean isTile(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return false;
        }
        return this.blocks[(y * this.height + z) * this.width + x] == 1;
    }

    public int getTile(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return 0;
        }
        return this.blocks[(y * this.height + z) * this.width + x] & 255;
    }

    public boolean isSolidTile(int x, int y, int z) {
        return this.isTile(x, y, z);
    }

    public boolean isLightBlocker(int x, int y, int z) {
        return this.isSolidTile(x, y, z);
    }

    private AABB obtainCube(float x0, float y0, float z0, float x1, float y1, float z1) {
        if (this.cubePoolUsed == this.cubePool.length) {
            AABB[] grown = new AABB[this.cubePool.length * 2];
            System.arraycopy(this.cubePool, 0, grown, 0, this.cubePool.length);
            this.cubePool = grown;
        }
        AABB box = this.cubePool[this.cubePoolUsed];
        if (box == null) {
            box = new AABB(x0, y0, z0, x1, y1, z1);
            this.cubePool[this.cubePoolUsed] = box;
        } else {
            box.set(x0, y0, z0, x1, y1, z1);
        }
        this.cubePoolUsed++;
        return box;
    }

    public ArrayList<AABB> getCubes(AABB box) {
        ArrayList<AABB> boxes = this.cubeList;
        boxes.clear();
        this.cubePoolUsed = 0;
        int x0 = (int) box.x0;
        int x1 = (int) (box.x1 + 1.0F);
        int y0 = (int) box.y0;
        int y1 = (int) (box.y1 + 1.0F);
        int z0 = (int) box.z0;
        int z1 = (int) (box.z1 + 1.0F);
        if (x0 < 0) x0 = 0;
        if (y0 < 0) y0 = 0;
        if (z0 < 0) z0 = 0;
        if (x1 > this.width) x1 = this.width;
        if (y1 > this.depth) y1 = this.depth;
        if (z1 > this.height) z1 = this.height;
        byte[] b = this.blocks;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                int row = (y * this.height + z0) * this.width + x;
                for (int z = z0; z < z1; z++) {
                    if (b[row] == 1) {
                        boxes.add(this.obtainCube(x, y, z, x + 1, y + 1, z + 1));
                    }
                    row += this.width;
                }
            }
        }
        return boxes;
    }

    public float getBrightness(int x, int y, int z) {
        float dark = 0.8F;
        float light = 1.0F;
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return light;
        }
        return y < this.lightDepths[x + z * this.width] ? dark : light;
    }

    public void setTile(int x, int y, int z, int type) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return;
        }
        int index = (y * this.height + z) * this.width + x;
        if (this.blocks[index] == (byte) type) {
            return;
        }
        int column = x + z * this.width;
        int oldDepth = this.lightDepths[column];
        this.blocks[index] = (byte) type;
        this.dirty = true;
        this.calcLightDepths(x, z, 1, 1);
        int newDepth = this.lightDepths[column];
        for (int i = 0; i < this.levelListeners.size(); i++) {
            this.levelListeners.get(i).tileChanged(x, y, z);
        }
        if (oldDepth != newDepth) {
            int lo = oldDepth < newDepth ? oldDepth : newDepth;
            int hi = oldDepth < newDepth ? newDepth : oldDepth;
            for (int i = 0; i < this.levelListeners.size(); i++) {
                this.levelListeners.get(i).lightChanged(x, z, lo, hi);
            }
        }
    }
}
