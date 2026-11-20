package engine;

import java.util.function.ToIntFunction;

/** Display-only fitting: never changes filenames used for file IO. */
public final class FileNameDisplay {
    private FileNameDisplay() { }

    /** Hide only the final JSON extension in game labels; keep the IO filename intact. */
    public static String label(final String filename) {
        return filename.toLowerCase(java.util.Locale.ROOT).endsWith(".json")
                ? filename.substring(0, filename.length() - 5) : filename;
    }

    public static String fit(final String value, final int width, final ToIntFunction<String> measure) {
        String visible = value.replaceAll("[\\p{Cntrl}]", " ");
        if (width <= 0) return "";
        if (measure.applyAsInt(visible) <= width) return visible;
        String marker = "...";
        if (measure.applyAsInt(marker) > width) return "";
        int[] points = visible.codePoints().toArray();
        int keep = points.length - 1;
        while (keep > 0) {
            int left = (keep + 1) / 2;
            int right = keep / 2;
            String candidate = new String(points, 0, left) + marker
                    + new String(points, points.length - right, right);
            if (measure.applyAsInt(candidate) <= width) return candidate;
            keep--;
        }
        return marker;
    }
}
