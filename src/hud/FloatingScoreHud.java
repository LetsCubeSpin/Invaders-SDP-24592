package hud;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import entity.EnemyShip;
import entity.Ship;

/**
 * Shows score and time-penalty feedback at the point of impact.
 */
public final class FloatingScoreHud {
    private static final long DURATION_MS = 950;
    private static final Font FONT = new Font("Monospaced", Font.BOLD, 16);
    private static final Color NORMAL = new Color(255, 225, 92);
    private static final Color BONUS = new Color(255, 160, 110);

    private static final Color TIME_PENALTY = new Color(255, 112, 112);

    private final List<Popup> popups = new ArrayList<>();

    /** Called at the exact moment the existing collision logic awards points. */
    public void show(final EnemyShip ship) {
        final int points = ship.getPointValue();
        if (points <= 0)
            return;

        popups.add(new Popup(ship.getPositionX() + ship.getWidth() / 2,
                ship.getPositionY() - 7, "+" + points, points >= 100 ? BONUS : NORMAL,
                false, System.currentTimeMillis()));
    }

    /** Shows the countdown deduction above the ship that was hit. */
    public void showTimePenalty(final Ship ship, final int seconds) {
        popups.add(new Popup(ship.getPositionX() + ship.getWidth() / 2,
                ship.getPositionY() - 7, "-" + seconds + "s", TIME_PENALTY,
                true, System.currentTimeMillis()));
    }

    /** Draws onto the normal back buffer; no second window or launcher. */
    public void draw(final Graphics2D source, final int width, final int height) {
        final Graphics2D g = (Graphics2D) source.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(FONT);
            final FontMetrics metrics = g.getFontMetrics();
            final long now = System.currentTimeMillis();
            final Iterator<Popup> iterator = popups.iterator();
            while (iterator.hasNext()) {
                final Popup popup = iterator.next();
                final long elapsed = now - popup.createdAt;
                if (elapsed >= DURATION_MS) {
                    iterator.remove();
                    continue;
                }
                final String label = popup.label;
                final int textWidth = metrics.stringWidth(label);
                final int x = Math.max(2, Math.min(width - textWidth - 2,
                        popup.centerX - textWidth / 2));
                final double progress = elapsed / (double) DURATION_MS;
                final int rise = popup.rises ? (int) (18 * progress) : 0;
                final int y = Math.max(54, Math.min(height - 4, popup.baselineY - rise));
                final float alpha = (float) (progress < 0.30
                        ? 1.0 : Math.max(0.0, (1.0 - progress) / 0.70));
                g.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, alpha));
                g.setColor(Color.BLACK);
                g.drawString(label, x + 1, y + 1);
                g.setColor(popup.color);
                g.drawString(label, x, y);
            }
        } finally {
            g.dispose();
        }
    }

    private record Popup(int centerX, int baselineY, String label, Color color,
                         boolean rises, long createdAt) { }
}
