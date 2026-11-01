import com.mojang.rubydung.Game;
import com.mojang.rubydung.HitResult;
import com.mojang.rubydung.InputState;
import com.mojang.rubydung.Player;
import com.mojang.rubydung.Raycaster;
import com.mojang.rubydung.level.ChunkMesh;
import com.mojang.rubydung.level.Level;
import com.mojang.rubydung.level.MeshBuilder;

import java.io.File;

/**
 * Desktop-side checks for the parts that do not touch Android/OpenGL.
 * Run after build.sh with:
 *   javac -d build/testclasses <pure sources> tools/LogicTest.java
 *   java -cp build/testclasses LogicTest
 */
public class LogicTest {
    private static int failures = 0;

    private static void check(boolean ok, String what) {
        if (ok) {
            System.out.println("  ok  " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what);
        }
    }

    private static void near(float a, float b, float eps, String what) {
        check(Math.abs(a - b) <= eps, what + " (" + a + " vs " + b + ")");
    }

    public static void main(String[] args) throws Exception {
        File dir = new File("build/testworld");
        dir.mkdirs();
        File save = new File(dir, "level.dat");
        if (save.exists()) {
            save.delete();
        }

        System.out.println("world generation");
        Level level = new Level(256, 256, 64, dir);
        check(level.width == 256 && level.height == 256 && level.depth == 64, "dimensions 256x64x256");
        check(level.isSolidTile(10, 42, 10), "surface block at y=42");
        check(!level.isSolidTile(10, 43, 10), "air above surface");
        check(!level.isSolidTile(10, 63, 10), "air at world top");
        near(level.getBrightness(10, 42, 10), 1.0F, 0.001F, "surface brightness");
        near(level.getBrightness(10, 41, 10), 0.8F, 0.001F, "underground brightness");

        System.out.println("physics");
        Player p = new Player(level);
        p.setPosition(128.0F, 60.0F, 128.0F);
        InputState in = new InputState();
        for (int i = 0; i < 400; i++) {
            p.tick(in);
        }
        check(p.onGround, "player lands");
        near(p.bb.y0, 43.0F, 0.05F, "feet rest on y=43");
        near(p.y, 44.62F, 0.06F, "eye height 1.62");
        float zBefore = p.z;
        in.forward = true;
        for (int i = 0; i < 30; i++) {
            p.tick(in);
        }
        check(p.z < zBefore - 0.4F, "forward input moves -Z at yaw 0");
        in.forward = false;
        p.setPosition(128.0F, 44.62F, 128.0F);
        p.onGround = true;
        in.jump = true;
        p.tick(in);
        in.jump = false;
        check(p.yd > 0.0F, "jump gives upward velocity");

        System.out.println("raycast / block interaction");
        p.setPosition(128.0F, 46.0F, 128.0F);
        p.xRot = 90.0F;
        p.yRot = 0.0F;
        HitResult down = Raycaster.cast(level, p.x, p.y, p.z, p.yRot, p.xRot, 5);
        check(down != null, "ray down hits ground");
        if (down != null) {
            check(down.x == 128 && down.y == 42 && down.z == 128, "hit block 128,42,128");
            check(down.face == 1, "hit top face (face 1)");
        }

        level.setTile(130, 46, 128, 1);
        p.xRot = 0.0F;
        p.yRot = 90.0F;
        HitResult side = Raycaster.cast(level, p.x, p.y, p.z, p.yRot, p.xRot, 5);
        check(side != null, "ray east hits placed block");
        if (side != null) {
            check(side.x == 130 && side.y == 46 && side.z == 128, "hit placed block");
            check(side.face == 4, "hit west face (face 4)");
        }

        level.setTile(128, 46, 128, 1);
        HitResult inside = Raycaster.cast(level, p.x, p.y, p.z, p.yRot, p.xRot, 5);
        check(inside != null && inside.x == 128 && inside.y == 46 && inside.z == 128,
                "raycast still hits a block containing the camera");
        level.setTile(128, 46, 128, 0);

        level.setTile(128, 43, 128, 1);
        check(level.isSolidTile(128, 43, 128), "place block above ground");
        near(level.getBrightness(128, 43, 128), 1.0F, 0.001F, "placed block is lit");
        level.setTile(128, 43, 128, 0);
        check(!level.isSolidTile(128, 43, 128), "break block");

        System.out.println("freeform touch picking (MCPE)");
        // Wall four blocks in front of a player looking along -Z.
        for (int x = 47; x <= 54; x++) {
            for (int y = 40; y <= 50; y++) {
                level.setTile(x, y, 46, 1);
            }
        }
        float ex = 50.5F;
        float ey = 44.5F;
        float ez = 50.5F;
        int w = 800;
        int h = 480;
        HitResult mid = Raycaster.castScreen(level, ex, ey, ez, 0.0F, 0.0F,
                w * 0.5F, h * 0.5F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 5);
        check(mid != null && mid.x == 50 && mid.y == 44 && mid.z == 46,
                "touch centre picks the block under the finger");
        HitResult right = Raycaster.castScreen(level, ex, ey, ez, 0.0F, 0.0F,
                w * 0.75F, h * 0.5F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 5);
        check(right != null && right.x > 50, "touch right of centre picks to the right");
        HitResult up = Raycaster.castScreen(level, ex, ey, ez, 0.0F, 0.0F,
                w * 0.5F, h * 0.25F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 5);
        check(up != null && up.y > 44, "touch above centre picks higher");
        HitResult downTouch = Raycaster.castScreen(level, ex, ey, ez, 0.0F, 0.0F,
                w * 0.5F, h * 0.75F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 5);
        check(downTouch != null && downTouch.y < 44, "touch below centre picks lower");

        HitResult centreRay = Raycaster.cast(level, ex, ey, ez, 0.0F, 0.0F, 5);
        check(centreRay != null && mid != null
                        && centreRay.x == mid.x && centreRay.y == mid.y && centreRay.z == mid.z,
                "screen-centre pick matches the old centre ray");

        // Same wall, camera rotated 90 degrees: screen-right must now be +Z.
        for (int z = 47; z <= 54; z++) {
            for (int y = 40; y <= 50; y++) {
                level.setTile(54, y, z, 1);
            }
        }
        HitResult yawMid = Raycaster.castScreen(level, ex, ey, ez, 90.0F, 0.0F,
                w * 0.5F, h * 0.5F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 5);
        HitResult yawRight = Raycaster.castScreen(level, ex, ey, ez, 90.0F, 0.0F,
                w * 0.75F, h * 0.5F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 5);
        check(yawMid != null && yawMid.x == 54, "yawed camera picks straight ahead");
        check(yawRight != null && yawRight.z > yawMid.z,
                "screen-right follows camera right after yaw");

        // Pitch sign: looking down, screen-top rays are shallower and hit
        // farther away; screen-bottom rays are steeper and hit closer.
        HitResult downMid = Raycaster.castScreen(level, 100.5F, 44.5F, 100.5F,
                0.0F, 30.0F, w * 0.5F, h * 0.5F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 16);
        HitResult downTop = Raycaster.castScreen(level, 100.5F, 44.5F, 100.5F,
                0.0F, 30.0F, w * 0.5F, h * 0.25F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 16);
        HitResult downBottom = Raycaster.castScreen(level, 100.5F, 44.5F, 100.5F,
                0.0F, 30.0F, w * 0.5F, h * 0.75F, w, h,
                Game.FOV_HALF_TAN, Game.CAMERA_BACK, 16);
        check(downMid != null && downMid.y == 42, "downward camera hits ground");
        check(downTop != null && downTop.z < downMid.z,
                "screen-top ray is shallower when looking down");
        check(downBottom != null && downBottom.z > downMid.z,
                "screen-bottom ray is steeper when looking down");

        for (int x = 47; x <= 54; x++) {
            for (int y = 40; y <= 50; y++) {
                level.setTile(x, y, 46, 0);
            }
        }
        for (int z = 47; z <= 54; z++) {
            for (int y = 40; y <= 50; y++) {
                level.setTile(54, y, z, 0);
            }
        }

        System.out.println("chunk meshing");
        Level fresh = new Level(256, 256, 64, new File("build/testworld-empty"));
        ChunkMesh interior = new ChunkMesh(16, 32, 16, 32, 48, 32);
        interior.build(fresh);
        check(interior.vertexCount == 256 * 4, "interior top chunk = 256 quads (got " + interior.vertexCount / 4 + ")");
        check(interior.indexCount == 256 * 6, "6 indices per quad");
        check(interior.data != null && interior.data.capacity() >= interior.vertexCount * 24, "interleaved stride/capacity");
        check(interior.positions.position() == 0, "position offset 0");
        check(interior.texCoords.position() == 12, "texture offset 12");
        check(interior.colours.position() == 20, "colour offset 20");
        check(interior.positions.limit() == interior.vertexCount * 24, "view limit");
        near(interior.minY, 42.0F, 0.001F, "tight min y");
        near(interior.maxY, 43.0F, 0.001F, "tight max y");

        ChunkMesh corner = new ChunkMesh(0, 32, 0, 16, 48, 16);
        corner.build(fresh);
        check(corner.vertexCount == (256 + 2 * 11 * 16) * 4, "world-edge side faces drawn (got " + corner.vertexCount / 4 + " quads)");

        ChunkMesh underground = new ChunkMesh(32, 0, 32, 48, 16, 48);
        underground.build(fresh);
        check(underground.vertexCount == 0, "solid underground chunk is empty");

        ChunkMesh top = new ChunkMesh(16, 48, 16, 32, 64, 32);
        fresh.setTile(20, 63, 20, 1);
        top.build(fresh);
        check(top.vertexCount == 6 * 4, "block at world top has all six faces (got " + top.vertexCount / 4 + ")");
        fresh.setTile(20, 63, 20, 0);

        MeshBuilder shared = new MeshBuilder();
        ChunkMesh again = new ChunkMesh(16, 32, 16, 32, 48, 32);
        again.build(fresh, shared);
        java.nio.ByteBuffer first = again.data;
        fresh.setTile(20, 43, 20, 1);
        again.build(fresh, shared);
        java.nio.ByteBuffer grown = again.data;
        check(again.vertexCount == 260 * 4, "placed block adds five faces and hides one");
        fresh.setTile(20, 43, 20, 0);
        again.build(fresh, shared);
        check(again.data == grown, "vertex block is reused between rebuilds");
        check(again.vertexCount == 256 * 4, "rebuild after place/break matches original");

        System.out.println("dirty tracking");
        Level wl = new Level(256, 256, 64, new File("build/testworld-dirty"));
        com.mojang.rubydung.level.WorldRenderer wr = new com.mojang.rubydung.level.WorldRenderer(wl);
        Player wp = new Player(wl);
        wp.setPosition(128.5F, 50.0F, 128.5F);
        long t0 = System.nanoTime();
        for (int i = 0; i < 400; i++) {
            wr.update(wp, 3500000L);
        }
        check(wr.meshCount() >= 81, "initial ring built (meshes " + wr.meshCount() + ")");
        wr.update(wp, 0L);
        int meshesBefore = wr.meshCount();
        wl.setTile(130, 43, 130, 1);
        wr.update(wp, 0L);
        check(wr.meshCount() == meshesBefore, "interior edit keeps mesh count");
        wl.setTile(130, 43, 130, 0);
        wr.update(wp, 0L);
        wr.update(wp, 100000000L);
        check(wr.pendingBuilds(wp) == 0, "light-affected chunks rebuilt within a normal budget");
        wl.setTile(130, 20, 130, 0);
        wr.update(wp, 0L);
        check(wr.pendingBuilds(wp) == 0, "urgent edit is rebuilt in the same frame even with zero budget");
        wl.setTile(130, 20, 130, 1);
        wr.update(wp, 0L);
        wl.setTile(131, 42, 131, 0);
        wr.update(wp, 0L);
        wl.setTile(131, 42, 131, 1);
        wr.update(wp, 0L);
        System.out.println("  (sim time for initial ring + edits: " + (System.nanoTime() - t0) / 1000000L + " ms)");

        System.out.println("level light notifications");
        final int[] events = new int[3];
        Level ll = new Level(256, 256, 64, new File("build/testworld-light"));
        ll.addListener(new com.mojang.rubydung.level.LevelListener() {
            public void tileChanged(int x, int y, int z) { events[0]++; }
            public void lightChanged(int x, int z, int yFrom, int yTo) { events[1]++; events[2] = yTo - yFrom; }
            public void allChanged() { }
        });
        ll.setTile(10, 43, 10, 1);
        check(events[0] == 1 && events[1] == 1, "placing above surface changes light depth");
        ll.setTile(10, 43, 10, 1);
        check(events[0] == 1, "setting the same block is ignored");
        ll.setTile(10, 20, 10, 0);
        check(events[0] == 2 && events[1] == 1, "digging below the top does not change light depth");
        near(ll.getBrightness(10, 43, 10), 1.0F, 0.001F, "top of placed block lit");
        near(ll.getBrightness(10, 42, 10), 0.8F, 0.001F, "shadowed under placed block");

        System.out.println("save/load");
        File dir2 = new File("build/testworld-save");
        dir2.mkdirs();
        File save2 = new File(dir2, "level.dat");
        if (save2.exists()) {
            save2.delete();
        }
        Level a = new Level(256, 256, 64, dir2);
        a.setTile(5, 43, 7, 1);
        a.save();
        check(save2.exists() && save2.length() > 0, "level.dat written");
        Level b = new Level(256, 256, 64, dir2);
        check(b.isSolidTile(5, 43, 7), "modified block survived save/load");
        check(!b.isSolidTile(5, 44, 7), "adjacent air survived save/load");
        save2.delete();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }
}
