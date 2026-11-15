import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The SDF image PICO's 3D icon pairs with each layer: white, its alpha rising from 0 at the layer's edge to 1 at RAMP pixels
 * inside (premultiplied: RGB = alpha), as the PICO template's icon_3d_sdf_*.png are.
 *   java IconSdf.java <layer.png> <sdf.png>
 * Inside distance by a two-pass 8SSEDT (exact enough for a 32 px ramp).
 */
public class IconSdf {
    static final int RAMP = 32;

    public static void main(String[] a) throws Exception {
        BufferedImage in = ImageIO.read(new File(a[0]));
        int w = in.getWidth(), h = in.getHeight();
        // Distance (dx, dy) from each inside pixel to the nearest outside pixel.
        int[] dx = new int[w * h], dy = new int[w * h];
        final int FAR = 1 << 14;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean inside = ((in.getRGB(x, y) >>> 24) & 0xFF) >= 128;
            int i = y * w + x;
            dx[i] = inside ? FAR : 0; dy[i] = inside ? FAR : 0;
        }
        // The canvas border counts as outside.
        int[][] pass1 = {{-1, 0}, {0, -1}, {-1, -1}, {1, -1}};
        int[][] pass2 = {{1, 0}, {0, 1}, {1, 1}, {-1, 1}};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) relax(dx, dy, w, h, x, y, pass1);
            for (int x = w - 1; x >= 0; x--) relax(dx, dy, w, h, x, y, new int[][]{{1, 0}});
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) relax(dx, dy, w, h, x, y, pass2);
            for (int x = 0; x < w; x++) relax(dx, dy, w, h, x, y, new int[][]{{-1, 0}});
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            double d = Math.sqrt((double) dx[i] * dx[i] + (double) dy[i] * dy[i]);
            int v = (int) Math.round(Math.min(1.0, d / RAMP) * 255);
            out.setRGB(x, y, (v << 24) | (v << 16) | (v << 8) | v);
        }
        ImageIO.write(out, "png", new File(a[1]));
    }

    static void relax(int[] dx, int[] dy, int w, int h, int x, int y, int[][] offs) {
        int i = y * w + x;
        if (dx[i] == 0 && dy[i] == 0) return;
        for (int[] o : offs) {
            int nx = x + o[0], ny = y + o[1];
            int cx, cy;
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) { cx = Math.abs(o[0]); cy = Math.abs(o[1]); }  // border: outside
            else { int j = ny * w + nx; cx = dx[j] + Math.abs(o[0]); cy = dy[j] + Math.abs(o[1]); }
            if ((long) cx * cx + (long) cy * cy < (long) dx[i] * dx[i] + (long) dy[i] * dy[i]) { dx[i] = cx; dy[i] = cy; }
        }
    }
}
