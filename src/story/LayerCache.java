// 초상화 선 그림을 크기와 입(턱) 벌어짐 단계별로 한 번만 그려 두고 다시 쓰게 하는 캐시
package story;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures of the lines of a portrait, each made once and kept. Drawing the
 * lines takes longer than a frame, so a picture is made for each width and
 * each opening of the mouth or jaw (a level), and the pictures of all the
 * levels are made in the background the first time a width is drawn.
 */
final class LayerCache {

    /** Makes the picture of one level at one width. */
    interface Painter {
        /**
         * @param level
         *            Opening of the mouth or jaw, in steps, 0 for shut.
         * @param width
         *            Width of the picture in pixels.
         * @return the picture.
         */
        BufferedImage render(int level, int width);
    }

    /** Most pictures kept; the cache starts again beyond it. */
    private static final int MAX_LAYERS = 160;

    /** Renders the pictures of the levels ahead of time. */
    private static final ExecutorService WARMER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "portrait-layer-warmer");
                thread.setDaemon(true);
                return thread;
            });

    private final Painter painter;
    private final int mostOpen;
    /** The pictures: the key is the level and the width. */
    private final Map<Long, BufferedImage> layers =
            new ConcurrentHashMap<Long, BufferedImage>();
    /** Widths whose levels are being rendered ahead of time. */
    private final Set<Integer> warmed = ConcurrentHashMap.newKeySet();

    /**
     * @param painter
     *            Makes the pictures.
     * @param mostOpen
     *            Highest level that is drawn.
     */
    LayerCache(final Painter painter, final int mostOpen) {
        this.painter = painter;
        this.mostOpen = mostOpen;
    }

    /**
     * @param level
     *            Opening of the mouth or jaw, in steps.
     * @param width
     *            Width of the picture in pixels.
     * @return the picture, made now if it was not there yet.
     */
    BufferedImage get(final int level, final int width) {
        if (this.layers.size() > MAX_LAYERS) {
            this.layers.clear();
            this.warmed.clear();
        }
        if (this.warmed.add(width)) {
            WARMER.execute(() -> {
                for (int open = 1; open <= this.mostOpen; open++) {
                    make(open, width);
                }
            });
        }
        return make(level, width);
    }

    private BufferedImage make(final int level, final int width) {
        return this.layers.computeIfAbsent(((long) level << 32) | width,
                key -> this.painter.render(level, width));
    }
}
