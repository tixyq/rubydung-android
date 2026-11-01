package com.mojang.rubydung;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import javax.microedition.khronos.opengles.GL10;

/** Small PNG -> GL texture loader that works with the API 1 bitmap classes. */
public class Textures {
    public static int load(GL10 gl, AssetManager assets, String name, boolean smooth) {
        return load(gl, assets, name, smooth, false);
    }

    public static int load(GL10 gl, AssetManager assets, String name, boolean smooth, boolean mipmaps) {
        InputStream in = null;
        Bitmap bitmap = null;
        try {
            in = assets.open(name);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeStream(in, null, options);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
        if (bitmap == null) {
            return 0;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();

        boolean powerOfTwo = (width & (width - 1)) == 0 && (height & (height - 1)) == 0;
        boolean useMips = mipmaps && powerOfTwo && width == height;

        IntBuffer ids = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        gl.glGenTextures(1, ids);
        int id = ids.get(0);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, id);
        int mag = smooth ? GL10.GL_LINEAR : GL10.GL_NEAREST;
        int min;
        if (useMips) {
            min = smooth ? GL10.GL_LINEAR_MIPMAP_LINEAR : GL10.GL_NEAREST_MIPMAP_LINEAR;
        } else {
            min = mag;
        }
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, min);
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, mag);
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        int level = 0;
        int w = width;
        int h = height;
        while (true) {
            upload(gl, level, w, h, pixels);
            if (!useMips || (w == 1 && h == 1)) {
                break;
            }
            int nw = w > 1 ? w / 2 : 1;
            int nh = h > 1 ? h / 2 : 1;
            pixels = downsample(pixels, w, h, nw, nh);
            w = nw;
            h = nh;
            level++;
        }
        return id;
    }

    private static void upload(GL10 gl, int level, int w, int h, int[] pixels) {
        ByteBuffer data = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < w * h; i++) {
            int c = pixels[i];
            data.put((byte) ((c >> 16) & 0xFF));
            data.put((byte) ((c >> 8) & 0xFF));
            data.put((byte) (c & 0xFF));
            data.put((byte) ((c >>> 24) & 0xFF));
        }
        data.position(0);
        gl.glTexImage2D(GL10.GL_TEXTURE_2D, level, GL10.GL_RGBA, w, h, 0,
                GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, data);
    }

    /** 2x2 box filter weighted by alpha, so transparent texels do not tint the edges. */
    private static int[] downsample(int[] src, int w, int h, int nw, int nh) {
        int[] out = new int[nw * nh];
        int sx = w / nw;
        int sy = h / nh;
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int dy = 0; dy < sy; dy++) {
                    for (int dx = 0; dx < sx; dx++) {
                        int c = src[(y * sy + dy) * w + x * sx + dx];
                        int ca = c >>> 24;
                        a += ca;
                        r += ((c >> 16) & 0xFF) * ca;
                        g += ((c >> 8) & 0xFF) * ca;
                        b += (c & 0xFF) * ca;
                    }
                }
                int n = sx * sy;
                if (a == 0) {
                    out[y * nw + x] = 0;
                } else {
                    out[y * nw + x] = ((a / n) << 24) | ((r / a) << 16) | ((g / a) << 8) | (b / a);
                }
            }
        }
        return out;
    }
}
