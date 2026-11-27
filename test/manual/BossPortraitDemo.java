// 스테이지 3 보스 초상화 두 개가 영상처럼 반복해서 움직이는 모습을 직접 확인하는 수동 데모(저장 파일 없음)
package manual;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import story.BossPortrait;

/**
 * Optional visual demo. Shows the two bosses of stage 3 large and at the size
 * of a story dialogue portrait (160 x 160), moving in their loops.
 */
public final class BossPortraitDemo {

    private BossPortraitDemo() {
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
                    int big = 440;
                    BossPortrait.draw(g, 0, 10, 10, big, big, t);
                    BossPortrait.draw(g, 1, big + 20, 10, big, big, t);
                    // Same size as in the story dialogue.
                    BossPortrait.draw(g, 0, 10, big + 20, 160, 160, t);
                    BossPortrait.draw(g, 1, big + 20, big + 20, 160, 160, t);
                }
            };
            panel.setBackground(Color.DARK_GRAY);
            panel.setPreferredSize(new Dimension(910, 640));
            JFrame frame = new JFrame("Stage 3 boss portraits");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            new Timer(33, event -> panel.repaint()).start();
        });
    }
}
