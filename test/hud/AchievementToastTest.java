package hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import engine.Core;
import engine.DrawManager;
import records.Achievement;
import records.AchievementUnlock;
import records.RecordsService;
import screen.Screen;

/** The achievement notice on the status strip: order, timing and where it is drawn. */
class AchievementToastTest {

    @TempDir Path temp;

    private static final AchievementUnlock FIRST = new AchievementUnlock(Achievement.FIRST_RUN, 1);
    private static final AchievementUnlock BRONZE = new AchievementUnlock(Achievement.EXTERMINATOR, 1);
    private static final AchievementUnlock SILVER = new AchievementUnlock(Achievement.EXTERMINATOR, 2);
    /** One game frame at 60 frames a second. */
    private static final long FRAME = 16;

    private final Deque<AchievementUnlock> queue = new ArrayDeque<AchievementUnlock>();
    private final AchievementToast toast = new AchievementToast(this.queue::pollFirst, this.queue::size);

    /**
     * Updates the toast every frame from the given time until it shows no
     * notice, as a game screen would.
     *
     * @return each unlock as it started showing, and how long it stayed.
     */
    private List<Object[]> playUntilEmpty(final long start) {
        return playUntilEmpty(start, FRAME);
    }

    /**
     * Updates the toast from the given time until it shows no notice, with
     * the frames taking the given times in turn.
     *
     * @return each unlock as it started showing, and how long it stayed.
     */
    private List<Object[]> playUntilEmpty(final long start, final long... frames) {
        List<Object[]> shown = new ArrayList<Object[]>();
        long started = start;
        int frame = 0;
        for (long now = start; now < start + 60_000; now += frames[frame++ % frames.length]) {
            AchievementUnlock next = this.toast.update(now);
            if (next != null) {
                if (!shown.isEmpty())
                    shown.get(shown.size() - 1)[1] = now - started;
                shown.add(new Object[] { next, null });
                started = now;
            } else if (this.toast.getCurrent() == null) {
                if (!shown.isEmpty())
                    shown.get(shown.size() - 1)[1] = now - started;
                return shown;
            }
        }
        throw new AssertionError("the notices never ended");
    }

    @Test
    void threeUnlocksAtOnceAreAllShownInOrderAndQuickerWhileOthersWait() {
        this.queue.addAll(Arrays.asList(FIRST, BRONZE, SILVER));
        List<Object[]> shown = playUntilEmpty(1_000);
        assertEquals(3, shown.size());
        assertSame(FIRST, shown.get(0)[0]);
        assertSame(BRONZE, shown.get(1)[0]);
        assertSame(SILVER, shown.get(2)[0]);
        assertDuration(AchievementToast.SHORTEST_SHOW_MILLIS, (Long) shown.get(0)[1], "two more wait");
        assertDuration(AchievementToast.SHORT_SHOW_MILLIS, (Long) shown.get(1)[1], "one more waits");
        assertDuration(AchievementToast.SHOW_MILLIS, (Long) shown.get(2)[1], "none waits");
    }

    private static void assertDuration(final long expected, final long actual, final String message) {
        assertTrue(actual >= expected && actual < expected + FRAME * 2,
                message + ": shown " + actual + " ms, expected " + expected);
    }

    @Test
    void theWaitingCountLeavesOutTheNoticeShown() {
        this.queue.addAll(Arrays.asList(FIRST, BRONZE, SILVER));
        this.toast.update(0);
        assertSame(FIRST, this.toast.getCurrent());
        assertEquals(2, this.toast.getWaiting());
    }

    @Test
    void anUnlockDuringANoticeWaitsForIt() {
        this.queue.add(FIRST);
        assertSame(FIRST, this.toast.update(0));
        this.queue.add(BRONZE);
        assertNull(this.toast.update(FRAME), "the first notice keeps its time");
        assertSame(FIRST, this.toast.getCurrent());
        assertEquals(1, this.toast.getWaiting());
    }

    @Test
    void timeWithoutAGameScreenDoesNotCount() {
        this.queue.addAll(Arrays.asList(FIRST, BRONZE));
        this.toast.update(0);
        this.toast.update(FRAME);
        // A stage change: no screen updates the notice for ten seconds,
        // then the next stage's screen opens.
        this.toast.screenStarted();
        assertNull(this.toast.update(10_000));
        assertSame(FIRST, this.toast.getCurrent(), "the notice carries on in the next stage");
        List<Object[]> rest = playUntilEmpty(10_000 + FRAME);
        assertSame(BRONZE, rest.get(0)[0], "the waiting notice follows it");
    }

    @Test
    void slowFramesStillMoveTheNoticesOn() {
        this.queue.addAll(Arrays.asList(FIRST, BRONZE, SILVER));
        long slow = 125;
        List<Object[]> shown = playUntilEmpty(1_000, 101, slow);
        assertEquals(3, shown.size(), "every notice is shown at 8 to 10 frames a second");
        assertSame(FIRST, shown.get(0)[0]);
        assertSame(BRONZE, shown.get(1)[0]);
        assertSame(SILVER, shown.get(2)[0]);
        assertSlowDuration(AchievementToast.SHORTEST_SHOW_MILLIS, (Long) shown.get(0)[1], slow);
        assertSlowDuration(AchievementToast.SHORT_SHOW_MILLIS, (Long) shown.get(1)[1], slow);
        assertSlowDuration(AchievementToast.SHOW_MILLIS, (Long) shown.get(2)[1], slow);
    }

    private static void assertSlowDuration(final long expected, final long actual, final long frame) {
        assertTrue(actual >= expected && actual <= expected + frame,
                "shown " + actual + " ms, expected " + expected);
    }

    @Test
    void itSlidesInStaysAndSlidesOut() {
        this.queue.add(FIRST);
        this.toast.update(0);
        assertEquals(0.0, this.toast.getShown(), 1e-9, "it starts off screen");
        long now = 0;
        while (now < AchievementToast.SLIDE_MILLIS / 2) {
            now += FRAME;
            this.toast.update(now);
        }
        double halfway = this.toast.getShown();
        assertTrue(halfway > 0.3 && halfway < 0.7, "halfway in: " + halfway);
        while (now < AchievementToast.SHOW_MILLIS / 2) {
            now += FRAME;
            this.toast.update(now);
        }
        assertEquals(1.0, this.toast.getShown(), 1e-9, "fully shown in the middle");
        while (now < AchievementToast.SHOW_MILLIS - AchievementToast.SLIDE_MILLIS / 2) {
            now += FRAME;
            this.toast.update(now);
        }
        assertTrue(this.toast.getShown() < 0.7, "sliding out at the end");
    }

    @Test
    void itsBorderFlashesOnlyJustAfterSlidingIn() {
        this.queue.add(FIRST);
        this.toast.update(0);
        int lit = 0;
        int dark = 0;
        for (long now = FRAME; now < AchievementToast.SHOW_MILLIS; now += FRAME) {
            this.toast.update(now);
            boolean flashing = this.toast.isFlashing();
            boolean flashTime = now >= AchievementToast.SLIDE_MILLIS
                    && now < AchievementToast.SLIDE_MILLIS + AchievementToast.FLASH_MILLIS;
            if (!flashTime)
                assertTrue(!flashing, "no flash at " + now + " ms");
            else if (flashing)
                lit++;
            else
                dark++;
        }
        assertTrue(lit > 0 && dark > 0, "it goes on and off: " + lit + " lit, " + dark + " dark");
    }

    @Test
    void clearingHidesTheNotice() {
        this.queue.add(FIRST);
        this.toast.update(0);
        this.toast.clear();
        assertNull(this.toast.getCurrent());
        assertEquals(0.0, this.toast.getShown());
        assertEquals(0, this.toast.getWaiting());
    }

    @Test
    void everyScreenOfAServiceSharesOneNotice() {
        RecordsService service = new RecordsService(this.temp.resolve("a.properties"));
        RecordsService other = new RecordsService(this.temp.resolve("b.properties"));
        try {
            assertSame(AchievementToast.of(service), AchievementToast.of(service));
            assertNotSame(AchievementToast.of(service), AchievementToast.of(other));
            service.startRun("run");
            service.enemyDefeated("run", "enemy-1");
            assertEquals(1, service.getPendingNotificationCount(), "First Contact waits");
            AchievementToast shared = AchievementToast.of(service);
            assertEquals("First Contact", shared.update(0).getTitle());
            assertEquals(0, service.getPendingNotificationCount());
        } finally {
            service.close();
            other.close();
        }
    }

    @Test
    void theNoticeStaysOnTheStripRightOfTheItems() throws Exception {
        DrawManager draw = Core.getDrawManager();
        Screen screen = new Screen(448, 520, 60);
        Field buffer = DrawManager.class.getDeclaredField("backBuffer");
        buffer.setAccessible(true);
        Path directory = Paths.get("out", "hud-tests");
        Files.createDirectories(directory);

        this.queue.addAll(Arrays.asList(BRONZE, FIRST, SILVER));
        // The middle of the first notice, which is short as two more wait.
        for (long now = 0; now <= AchievementToast.SHORTEST_SHOW_MILLIS / 2; now += FRAME)
            this.toast.update(now);
        assertSame(BRONZE, this.toast.getCurrent());
        assertEquals(1.0, this.toast.getShown(), 1e-9);
        // Wide room, and room for only part of the name.
        int[] freeFrom = { 0, 120, screen.getWidth() - 90 };
        for (int free : freeFrom) {
            draw.initDrawing(screen);
            BufferedImage image = (BufferedImage) buffer.get(null);
            BufferedImage before = copy(image);
            draw.drawAchievementToast(screen, this.toast, free);
            int changed = 0;
            for (int x = 0; x < image.getWidth(); x++)
                for (int y = 0; y < image.getHeight(); y++)
                    if (image.getRGB(x, y) != before.getRGB(x, y)) {
                        changed++;
                        assertTrue(y >= DrawManager.STATUS_STRIP_TOP
                                && y < DrawManager.STATUS_STRIP_TOP + DrawManager.STATUS_STRIP_HEIGHT,
                                "drawn at y " + y + ", outside the strip");
                        assertTrue(x >= free && x < screen.getWidth(),
                                "drawn at x " + x + ", over the items or off screen");
                    }
            assertTrue(changed > 0, "the notice is drawn with " + free + " free");
            javax.imageio.ImageIO.write(image, "png",
                    directory.resolve("toast-free-from-" + free + ".png").toFile());
            draw.completeDrawing(screen);
        }
    }

    private static BufferedImage copy(final BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        return copy;
    }
}
