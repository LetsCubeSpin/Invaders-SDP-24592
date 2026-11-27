// 관제탑 직원 초상화를 조종사 초상화와 나란히 움직이는 모습으로 확인하는 수동 데모(저장 파일 없음)
package manual;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import story.ControllerPortrait;
import story.PilotPortraitTraced;

/**
 * Optional visual demo. Shows the control tower portrait large and at the size
 * of the story dialogue (115 x 160), next to the pilot for comparison.
 */
public final class ControllerPortraitDemo {

    private ControllerPortraitDemo() {
    }

    public static void main(final String[] args) {
        SwingUtilities.invokeLater(() -> {
            final long start = System.currentTimeMillis();
            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(final Graphics g0) {
                    super.paintComponent(g0);
                    Graphics2D g = (Graphics2D) g0;
                    double t = (System.currentTimeMillis() - start) / 1000.0;
                    int h = 500;
                    int w = (int) Math.round(h * ControllerPortrait.SRC_W
                            / ControllerPortrait.SRC_H);
                    // She speaks for four seconds, then stays quiet for two.
                    boolean speaking = t % 6 < 4;
                    ControllerPortrait.draw(g, 10, 10, w, h, t, speaking);
                    // Same size as in the story dialogue.
                    ControllerPortrait.draw(g, w + 30, 10, 115, 160, t, speaking);
                    PilotPortraitTraced.draw(g, w + 30, 190, 115, 160, t);
                }
            };
            panel.setBackground(Color.DARK_GRAY);
            panel.setPreferredSize(new Dimension(520, 520));
            JFrame frame = new JFrame("Control tower portrait");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            new Timer(33, event -> panel.repaint()).start();
        });
    }
}
