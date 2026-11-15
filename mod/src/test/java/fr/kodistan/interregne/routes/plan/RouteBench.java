package fr.kodistan.interregne.routes.plan;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline bench of the route planner, for tuning it without the game: plans routes on made-up reliefs or on real
 * terrain dumped by dev.TerrainDump, prints what each route is made of and renders a picture of it (map and
 * profile). Run its main with an output directory, then either nothing (all the made-up reliefs), part of a name, or
 * a .rter file followed by name:ax,az,bx,bz routes.
 */
public final class RouteBench
{
    static final int SEA = 62;

    interface Shape
    {
        double height(int x, int z);
    }

    static double noise(final int xi, final int zi, final int seed)
    {
        long h = xi * 0x9E3779B97F4A7C15L ^ zi * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h & 0xFFFFFF) / (double) 0xFFFFFF) * 2 - 1;
    }

    static double smooth(final double x, final double z, final int seed)
    {
        final int xi = (int) Math.floor(x);
        final int zi = (int) Math.floor(z);
        double fx = x - xi;
        double fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        final double a = noise(xi, zi, seed);
        final double b = noise(xi + 1, zi, seed);
        final double c = noise(xi, zi + 1, seed);
        final double d = noise(xi + 1, zi + 1, seed);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    static double fbm(final double x, final double z, final double wavelength, final int octaves, final int seed)
    {
        double total = 0;
        double amp = 1;
        double norm = 0;
        double f = 1 / wavelength;
        for (int o = 0; o < octaves; o++)
        {
            total += amp * smooth(x * f, z * f, seed + o * 31);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return total / norm;
    }

    /** Builds a field from a height function; anything below SEA is water with the function as its bed. */
    static HeightField synth(final int w, final int h, final Shape shape)
    {
        final HeightField f = new HeightField(0, 0, w, h);
        for (int z = 0; z < h; z++)
        {
            for (int x = 0; x < w; x++)
            {
                final int y = (int) Math.round(shape.height(x, z));
                if (y < SEA)
                {
                    f.set(x, z, SEA, y, HeightField.WATER);
                }
                else
                {
                    f.set(x, z, y, y, 0);
                }
            }
        }
        return f;
    }

    record Case(String name, HeightField field, int ax, int az, int bx, int bz)
    {
    }

    static List<Case> synthetic()
    {
        final List<Case> cases = new ArrayList<>();
        // 1. rolling plain, a river 14 wide across the way, banks one above the water
        cases.add(new Case("plain_river", synth(360, 200, (x, z) -> {
            final double river = Math.abs(x - 180 - 18 * Math.sin(z / 40.0));
            final double land = 66 + 3 * fbm(x, z, 60, 3, 1);
            return river < 7 ? 58 : Math.min(land, 63 + (river - 7) * 0.8);
        }), 20, 100, 340, 110));
        // 2. two plateaus, a canyon 26 wide and 28 deep between them, a stream at the bottom
        cases.add(new Case("canyon", synth(320, 200, (x, z) -> {
            final double c = Math.abs(x - 160 - 10 * Math.sin(z / 30.0));
            final double plateau = 92 + 2 * fbm(x, z, 50, 3, 2);
            if (c < 13)
            {
                return c < 3 ? 60 : 64 + (c - 3) * 2.8;
            }
            return plateau;
        }), 20, 90, 300, 120));
        // 3. a long ridge 44 high across the way, no way around
        cases.add(new Case("ridge", synth(360, 240, (x, z) -> {
            final double d = Math.abs(x - 180);
            final double ridge = 44 * Math.exp(-d * d / (2 * 22 * 22.0));
            return 68 + ridge + 2.5 * fbm(x, z, 45, 3, 3);
        }), 20, 120, 340, 120));
        // 4. a lone hill 30 high on the straight line
        cases.add(new Case("hill", synth(360, 240, (x, z) -> {
            final double d2 = (x - 180) * (x - 180) + (z - 120) * (z - 120);
            return 68 + 30 * Math.exp(-d2 / (2 * 30 * 30.0)) + 2 * fbm(x, z, 45, 3, 4);
        }), 20, 120, 340, 120));
        // 5. rolling hills, a diagonal trip
        cases.add(new Case("rolling_diagonal", synth(520, 520, (x, z) -> 78 + 16 * fbm(x, z, 110, 4, 5)), 40, 50, 470, 460));
        // 6. a wide shallow valley with a river at the bottom
        cases.add(new Case("wide_valley", synth(520, 200, (x, z) -> {
            final double d = Math.abs(x - 260);
            final double valley = 86 - 26 * Math.exp(-d * d / (2 * 70 * 70.0));
            return d < 6 ? 57 : valley + 2 * fbm(x, z, 50, 3, 6);
        }), 20, 100, 500, 100));
        // 7. a mountain range with a pass off the straight line
        cases.add(new Case("pass", synth(420, 420, (x, z) -> {
            final double d = Math.abs(x - 210);
            final double along = 60 * (1 - 0.75 * Math.exp(-(z - 330) * (z - 330) / (2 * 35 * 35.0)));
            return 70 + along * Math.exp(-d * d / (2 * 38 * 38.0)) + 3 * fbm(x, z, 50, 3, 7);
        }), 30, 150, 390, 150));
        // 8. a lake on the straight line
        cases.add(new Case("lake", synth(420, 300, (x, z) -> {
            final double d = Math.sqrt((x - 210) * (x - 210) + (z - 150) * (z - 150) * 1.6);
            return 66 + 2 * fbm(x, z, 50, 3, 8) - 12 * Math.exp(-d * d / (2 * 45 * 45.0)) * 1.4;
        }), 20, 150, 400, 150));
        // 9. steep slope: the far end sits 45 up a mountainside
        cases.add(new Case("climb", synth(360, 300, (x, z) -> 66 + 55 / (1 + Math.exp(-(x - 200) / 22.0)) + 3 * fbm(x, z, 45, 3, 9)), 20, 150, 330, 150));
        // 10. terraced gorge country: several ravines in a row
        cases.add(new Case("ravines", synth(520, 240, (x, z) -> {
            double y = 84 + 3 * fbm(x, z, 60, 3, 10);
            for (final int cx : new int[] {130, 260, 390})
            {
                final double c = Math.abs(x - cx - 12 * Math.sin((z + cx) / 35.0));
                if (c < 9)
                {
                    y = Math.min(y, 66 + c * 2.2);
                }
            }
            return y;
        }), 20, 120, 500, 120));
        // 11. long haul over mixed country: ridges, rivers, per-block roughness
        cases.add(new Case("long_mixed", synth(2300, 700, (x, z) -> {
            final double base = 72 + 22 * fbm(x, z, 260, 4, 11) + 9 * fbm(x, z, 60, 3, 12) + 0.9 * noise(x, z, 13);
            final double ridge = 38 * Math.exp(-Math.pow((x - 900 - 0.3 * z) / 40.0, 2)) + 30 * Math.exp(-Math.pow((x - 1600 + 0.2 * z) / 55.0, 2));
            final double river = Math.abs(x - 500 - 60 * Math.sin(z / 90.0));
            final double river2 = Math.abs(x - 1250 - 40 * Math.sin(z / 70.0));
            double y = base + ridge;
            if (river < 30) y = Math.min(y, 56 + river * 0.9);
            if (river2 < 22) y = Math.min(y, 55 + river2 * 1.6);
            return y;
        }), 60, 350, 2240, 380));
        // 12. long diagonal haul
        cases.add(new Case("long_diagonal", synth(1500, 1500, (x, z) -> {
            final double base = 74 + 24 * fbm(x, z, 240, 4, 21) + 8 * fbm(x, z, 55, 3, 22) + 0.9 * noise(x, z, 23);
            final double river = Math.abs((x + z) / 1.414 - 1060 - 50 * Math.sin((x - z) / 120.0));
            return river < 26 ? Math.min(base, 55 + river * 1.2) : base;
        }), 120, 100, 1380, 1400));
        return cases;
    }

    public static void main(final String[] args) throws Exception
    {
        final Path out = Path.of(args[0]);
        Files.createDirectories(out);
        final List<Case> cases = new ArrayList<>();
        if (args.length > 1 && args[1].endsWith(".rter"))
        {
            // real terrain: file then "name:ax,az,bx,bz" ...
            final HeightField full;
            try (var in = new BufferedInputStream(Files.newInputStream(Path.of(args[1]))))
            {
                full = HeightField.read(in);
            }
            for (int a = 2; a < args.length; a++)
            {
                final String[] parts = args[a].split(":");
                final String[] c = parts[1].split(",");
                final int ax = Integer.parseInt(c[0]);
                final int az = Integer.parseInt(c[1]);
                final int bx = Integer.parseInt(c[2]);
                final int bz = Integer.parseInt(c[3]);
                final int pad = 96 + (int) (0.25 * Math.hypot(bx - ax, bz - az));
                cases.add(new Case(parts[0], full.crop(Math.min(ax, bx) - pad, Math.min(az, bz) - pad, Math.max(ax, bx) + pad, Math.max(az, bz) + pad),
                  ax, az, bx, bz));
            }
        }
        else
        {
            for (final Case c : synthetic())
            {
                if (args.length < 2 || c.name().contains(args[1]))
                {
                    cases.add(c);
                }
            }
        }
        for (final Case c : cases)
        {
            final long t0 = System.nanoTime();
            final RoutePlan plan = RoutePlanner.plan(c.field(), c.ax(), c.az(), c.bx(), c.bz(), Long.getLong("route.budget", 4000L));
            final double ms = (System.nanoTime() - t0) / 1e6;
            if (plan == null)
            {
                System.out.println(c.name() + ": NO ROUTE (" + (int) ms + " ms)");
                continue;
            }
            final String stats = stats(c, plan, ms);
            System.out.println(stats);
            render(c, plan, stats, out.resolve(c.name() + ".png").toFile());
        }
    }

    static String stats(final Case c, final RoutePlan p, final double ms)
    {
        final int n = p.length();
        int steps = 0;
        int flight = 0;
        int longest = 0;
        int turns = 0;
        int maxCut = 0;
        int maxFill = 0;
        final StringBuilder structures = new StringBuilder();
        for (int i = 1; i < n; i++)
        {
            if (p.y[i] != p.y[i - 1])
            {
                steps++;
                flight++;
                longest = Math.max(longest, flight);
            }
            else
            {
                flight = 0;
            }
            if (i < n - 1 && (p.x[i] - p.x[i - 1] != p.x[i + 1] - p.x[i] || p.z[i] - p.z[i - 1] != p.z[i + 1] - p.z[i]))
            {
                turns++;
            }
            if (p.mode[i] == RoutePlan.SURFACE)
            {
                final int g = c.field().ground(p.x[i], p.z[i]);
                maxCut = Math.max(maxCut, g - p.y[i]);
                maxFill = Math.max(maxFill, p.y[i] - g);
            }
        }
        int i = 0;
        while (i < n)
        {
            final byte m = p.mode[i];
            int j = i;
            while (j + 1 < n && p.mode[j + 1] == m)
            {
                j++;
            }
            if (m != RoutePlan.SURFACE)
            {
                int extreme = 0;
                int lowY = Integer.MAX_VALUE;
                int highY = Integer.MIN_VALUE;
                for (int k = i; k <= j; k++)
                {
                    final int g = c.field().ground(p.x[k], p.z[k]);
                    extreme = Math.max(extreme, Math.abs(p.y[k] - g));
                    lowY = Math.min(lowY, p.y[k]);
                    highY = Math.max(highY, p.y[k]);
                }
                structures.append(m == RoutePlan.BRIDGE ? " B" : " T").append('[').append(i).append("+").append(j - i + 1).append(" y").append(lowY)
                  .append(lowY == highY ? "" : ".." + highY).append(m == RoutePlan.BRIDGE ? " h" : " d").append(extreme).append(']');
            }
            i = j + 1;
        }
        final double crow = Math.hypot(c.bx() - c.ax(), c.bz() - c.az());
        return String.format("%s: len %d (crow %.0f) turns %d steps %d longestFlight %d maxCut %d maxFill %d cost %.0f %.0f ms |%s",
          c.name(), n, crow, turns, steps, longest, maxCut, maxFill, p.cost, ms, structures);
    }

    static void render(final Case c, final RoutePlan p, final String stats, final File file) throws Exception
    {
        final HeightField f = c.field();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < p.length(); i++)
        {
            minX = Math.min(minX, p.x[i]);
            maxX = Math.max(maxX, p.x[i]);
            minZ = Math.min(minZ, p.z[i]);
            maxZ = Math.max(maxZ, p.z[i]);
        }
        final int margin = 40;
        minX = Math.max(f.x0, minX - margin);
        minZ = Math.max(f.z0, minZ - margin);
        maxX = Math.min(f.x0 + f.w - 1, maxX + margin);
        maxZ = Math.min(f.z0 + f.h - 1, maxZ + margin);
        final int mw = maxX - minX + 1;
        final int mh = maxZ - minZ + 1;
        final double scale = Math.min(4.0, Math.min(1400.0 / mw, 900.0 / mh));
        final int iw = Math.max(900, (int) (mw * scale));
        final int ih = (int) (mh * scale);
        final int chart = 300;
        final BufferedImage img = new BufferedImage(iw, ih + chart + 24, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < ih; py++)
        {
            for (int px = 0; px < (int) (mw * scale); px++)
            {
                final int x = minX + (int) (px / scale);
                final int z = minZ + (int) (py / scale);
                img.setRGB(px, py, colour(f, p, x, z));
            }
        }
        final Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.fillRect(0, ih, iw, chart + 24);
        g.setColor(Color.BLACK);
        g.drawString(stats.length() > 200 ? stats.substring(0, 200) : stats, 4, ih + 14);

        // profile
        final int n = p.length();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        final int[] ground = new int[n];
        final int[] bed = new int[n];
        for (int i = 0; i < n; i++)
        {
            ground[i] = f.ground(p.x[i], p.z[i]);
            bed[i] = f.bed[f.index(p.x[i], p.z[i])];
            lowest = Math.min(lowest, Math.min(bed[i], p.y[i]));
            highest = Math.max(highest, Math.max(ground[i], p.y[i]));
        }
        lowest -= 4;
        highest += 6;
        final double sx = (iw - 20) / (double) n;
        final double sy = Math.min(6.0, (chart - 10) / (double) (highest - lowest));
        final int baseY = ih + 24 + chart - 5;
        for (int i = 0; i < n; i++)
        {
            final int px = 10 + (int) (i * sx);
            final int pw = Math.max(1, (int) Math.ceil(sx));
            final boolean wet = f.wet(p.x[i], p.z[i]);
            g.setColor(new Color(150, 140, 125));
            final int gTop = baseY - (int) ((bed[i] - lowest) * sy);
            g.fillRect(px, gTop, pw, baseY - gTop);
            if (wet)
            {
                g.setColor(new Color(70, 130, 220));
                final int wTop = baseY - (int) ((ground[i] - lowest) * sy);
                g.fillRect(px, wTop, pw, gTop - wTop);
            }
            g.setColor(p.mode[i] == RoutePlan.BRIDGE ? new Color(220, 0, 0) : p.mode[i] == RoutePlan.TUNNEL ? new Color(190, 0, 230) : new Color(20, 20, 20));
            final int rTop = baseY - (int) ((p.y[i] + 1 - lowest) * sy);
            g.fillRect(px, rTop, pw, Math.max(2, (int) sy));
            if (p.mode[i] == RoutePlan.TUNNEL)
            {
                g.setColor(new Color(240, 220, 250));
                g.fillRect(px, rTop - (int) (5 * sy), pw, (int) (5 * sy));
            }
        }
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(0, 0, 0, 60));
        for (int level = (lowest / 10) * 10; level <= highest; level += 10)
        {
            final int ly = baseY - (int) ((level - lowest) * sy);
            g.drawLine(10, ly, iw - 10, ly);
            g.drawString(String.valueOf(level), 12, ly - 2);
        }
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    static int colour(final HeightField f, final RoutePlan p, final int x, final int z)
    {
        if (!f.inside(x, z))
        {
            return 0;
        }
        final int owner = p.footprint.owner(x, z);
        if (owner >= 0 && Footprint.road(p.footprint.distanceSq(x, z)))
        {
            final byte m = p.mode[owner];
            if (owner == 0 || owner == p.length() - 1)
            {
                return 0xFFFFFF;
            }
            return m == RoutePlan.BRIDGE ? 0xE00000 : m == RoutePlan.TUNNEL ? 0xC000F0 : (p.y[owner] != p.y[Math.max(0, owner - 1)] ? 0x705030 : 0xFFB020);
        }
        final int flag = f.flags(x, z);
        if ((flag & HeightField.UNKNOWN) != 0)
        {
            return 0x202020;
        }
        final int y = f.ground(x, z);
        if ((flag & (HeightField.WATER | HeightField.ICE)) != 0)
        {
            final int depth = Math.min(20, y - f.bed[f.index(x, z)]);
            return new Color(40, 90 - depth * 2, 200 - depth * 4).getRGB() & 0xFFFFFF;
        }
        if ((flag & HeightField.BLOCKED) != 0)
        {
            return 0x808080;
        }
        // hypsometric tint + hillshade from the north-west
        final double t = Math.max(0, Math.min(1, (y - SEA) / 120.0));
        double r = 110 + 130 * t;
        double gr = 160 + 40 * t - 60 * t * t;
        double b = 90 + 120 * t * t;
        final int west = f.inside(x - 1, z) ? f.ground(x - 1, z) : y;
        final int north = f.inside(x, z - 1) ? f.ground(x, z - 1) : y;
        final double shade = Math.max(0.55, Math.min(1.25, 1 + 0.09 * ((y - west) + (y - north))));
        final double contour = y % 10 == 0 ? 0.88 : 1;
        r *= shade * contour;
        gr *= shade * contour;
        b *= shade * contour;
        return (clamp(r) << 16) | (clamp(gr) << 8) | clamp(b);
    }

    static int clamp(final double v)
    {
        return (int) Math.max(0, Math.min(255, v));
    }
}
