package engine;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.prefs.Preferences;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import audio.AudioManager;
import entity.ShipType;
import screen.GameScreen;
import shop.PreviewPreferencesFactory;
import story.PlaceholderStoryScenes;
import story.StoryProgress;
import story.StoryRoute;

/** Development launcher UI and isolated battle entry point, excluded from the application jar. */
public final class StoryDebugLauncher {
    private StoryDebugLauncher() { }

    static void requireDebugSession() {
        if (!Boolean.getBoolean("invaders.storyDebug")) {
            throw new IllegalStateException("Enable the launcher with scripts/run-story-debug.ps1 (Windows) or scripts/run-story-debug.sh (Linux/macOS).");
        }
        if (!PreviewPreferencesFactory.class.getName().equals(System.getProperty("java.util.prefs.PreferencesFactory"))
                || Preferences.userRoot().getClass() != new PreviewPreferencesFactory().userRoot().getClass()
                || Core.getRecordsService() != null) {
            throw new IllegalStateException("Story debug requires a fresh JVM with memory-only preferences.");
        }
    }

    public static void main(String[] args) throws Exception {
        StoryDebugController.main(args);
    }

    /** Child JVM entry point. The supervisor owns selection, replay, and session cleanup. */
    static final class Battle {
        public static void main(String[] args) throws Exception {
            requireDebugSession();
            StoryDebugSelection choice = StoryDebugSelection.parse(args);
            System.out.println(choice.describe());
            Frame frame = new Frame(448, 520);
            int outcome;
            try {
                frame.setTitle("Story debug | " + choice.route() + " | Stage " + choice.stage());
                Core.getDrawManager().setFrame(frame);
                outcome = frame.setScreen(createScreen(choice, frame.getWidth(), frame.getHeight(),
                        new StoryDebugBattleControls(frame)));
            } finally {
                AudioManager.stopBGM();
                frame.dispose();
            }
            if (outcome == GameScreen.RETURN_TO_STORY_DEBUG) System.exit(outcome);
        }
    }

    static GameScreen createScreen(StoryDebugSelection choice, int width, int height) {
        return createScreen(choice, width, height, null);
    }

    static GameScreen createScreen(StoryDebugSelection choice, int width, int height, BooleanSupplier returnToLauncher) {
        requireDebugSession();
        GameState state = new GameState(choice.stage(), 0, choice.modifiers().startingLives(), 0, 0);
        return GameScreen.forStoryDebugStage(state, choice.progress(),
                settings -> Core.applyDifficulty(settings, GameplayConfig.getMultiplier()),
                width, height, 60, choice.modifiers(), returnToLauncher);
    }

    static StoryDebugSelection defaultSelection() {
        return new StoryDebugSelection(new PlaceholderStoryScenes().getRoute(), 1,
                StoryDebugSelection.Difficulty.EASY, ShipType.STANDARD);
    }

    record Picker(JPanel panel, Supplier<StoryDebugSelection> selection) { }

    static StoryDebugSelection choose(StoryDebugSelection previous) {
        Picker picker = picker(previous);
        Object[] buttons = {"Start stage", "Cancel"};
        int result = JOptionPane.showOptionDialog(null, picker.panel(), "LAST LINE | Story debug",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, buttons, buttons[0]);
        return result == 0 ? picker.selection().get() : null;
    }

    static Picker picker(StoryDebugSelection previous) {
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 6));
        JComboBox<StoryRoute> route = addField(fields, "Story route", StoryRoute.values());
        route.setSelectedItem(previous.route());
        Integer[] stages = new Integer[StoryProgress.TOTAL_STAGES];
        for (int i = 0; i < stages.length; i++) stages[i] = i + 1;
        JComboBox<Integer> stage = addField(fields, "Stage", stages);
        stage.setSelectedItem(previous.stage());
        fields.add(new JLabel("Difficulty"));
        fields.add(new JLabel(StoryDebugSelection.Difficulty.EASY.name()));
        fields.add(new JLabel("Ship"));
        fields.add(new JLabel(ShipType.STANDARD.name()));

        StoryDebugModifiers previousModifiers = previous.modifiers();
        JComboBox<String> invincible = addField(fields, "Invincible", new String[]{"OFF", "ON"});
        invincible.setSelectedIndex(previousModifiers.invincible() ? 1 : 0);
        Integer[] livesRange = IntStream.rangeClosed(StoryDebugModifiers.MIN_LIVES, StoryDebugModifiers.MAX_LIVES)
                .boxed().toArray(Integer[]::new);
        JComboBox<Integer> lives = addField(fields, "Starting lives", livesRange);
        lives.setSelectedItem(previousModifiers.startingLives());
        JComboBox<Double> hp = addMultiplier(fields, "Enemy HP multiplier", previousModifiers.enemyHpMultiplier());
        JComboBox<Double> speed = addMultiplier(fields, "Enemy speed multiplier", previousModifiers.enemySpeedMultiplier());

        Supplier<StoryDebugSelection> selection = () -> new StoryDebugSelection(
                route.getItemAt(route.getSelectedIndex()), stage.getItemAt(stage.getSelectedIndex()),
                StoryDebugSelection.Difficulty.EASY, ShipType.STANDARD,
                new StoryDebugModifiers(invincible.getSelectedIndex() == 1, lives.getItemAt(lives.getSelectedIndex()),
                        hp.getItemAt(hp.getSelectedIndex()), speed.getItemAt(speed.getSelectedIndex())));
        JTextArea preview = new JTextArea();
        preview.setEditable(false);
        preview.setFocusable(false);
        preview.setBackground(Color.BLACK);
        preview.setForeground(Color.GREEN);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        preview.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        ActionListener refresh = event -> preview.setText(selection.get().describe());
        for (JComboBox<?> field : new JComboBox<?>[]{route, stage, invincible, lives, hp, speed})
            field.addActionListener(refresh);
        preview.setText(selection.get().describe());
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(fields, BorderLayout.CENTER);
        panel.add(preview, BorderLayout.SOUTH);
        return new Picker(panel, selection);
    }

    private static JComboBox<Double> addMultiplier(JPanel panel, String name, double previous) {
        JComboBox<Double> field = addField(panel, name, new Double[]{0.25, 0.5, 1.0, 2.0, 4.0});
        field.setSelectedItem(previous);
        if (!Double.valueOf(previous).equals(field.getSelectedItem())) {
            field.addItem(previous);
            field.setSelectedItem(previous);
        }
        return field;
    }

    private static <T> JComboBox<T> addField(JPanel panel, String name, T[] values) {
        JComboBox<T> field = new JComboBox<>(values);
        JLabel label = new JLabel(name);
        label.setLabelFor(field);
        panel.add(label);
        panel.add(field);
        return field;
    }
}
