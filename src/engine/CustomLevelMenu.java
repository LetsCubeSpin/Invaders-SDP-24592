package engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import level.LevelDefinition;

/** Session-local library. Each imported source keeps its original filename and definition. */
public final class CustomLevelMenu {
    public enum Action {
        IMPORT("Import JSON"), PLAY("Play Level"), EXPORT("Export JSON"), BACK("Back");

        private final String label;
        Action(final String label) { this.label = label; }
        public String getLabel() { return label; }
        public boolean isEnabled(final int levelCount) {
            return levelCount > 0 || this == IMPORT || this == BACK;
        }
    }

    /** Explicit UI order, independent of the enum declaration order. */
    public static final List<Action> MENU_ACTIONS = List.of(Action.IMPORT, Action.PLAY, Action.EXPORT, Action.BACK);
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(CustomLevelMenu.class.getName());

    public static final class Entry {
        private final Path source;
        private final CustomLevelSession session;

        private Entry(final Path source, final CustomLevelSession session) {
            this.source = source;
            this.session = session;
        }
        public Path getSource() { return source; }
        public String getFilename() { return source.getFileName().toString(); }
        public LevelDefinition getLevel() { return session.getLoadedLevel().orElseThrow(); }
    }

    public interface View {
        Action chooseAction(List<Entry> entries, int selected);
        List<Path> chooseImports();
        int chooseLevel(List<Entry> entries, int selected, Action action);
        Path chooseExportDirectory(String filename);
        boolean confirmOverwrite(Path destination);
        void showError(String message);
        void showResult(GameState result);
    }

    private final Supplier<CustomLevelSession> sessions;
    private final View view;
    private final List<Entry> entries = new ArrayList<>();
    private int selected = -1;

    public CustomLevelMenu(final Supplier<CustomLevelSession> sessions, final View view) {
        this.sessions = java.util.Objects.requireNonNull(sessions);
        this.view = java.util.Objects.requireNonNull(view);
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<Entry> snapshot = Collections.unmodifiableList(new ArrayList<>(entries));
                Action action = view.chooseAction(snapshot, selected);
                if (action == Action.BACK) return;
                if (!action.isEnabled(entries.size())) continue;
                if (action == Action.IMPORT) {
                    for (Path source : view.chooseImports()) {
                        if (!importLevel(source)) return;
                    }
                    continue;
                }
                int choice = view.chooseLevel(snapshot, selected, action);
                if (choice < 0) continue;
                if (choice >= entries.size()) throw new IllegalArgumentException("Unknown level selection");
                selected = choice;
                Entry entry = entries.get(choice);
                if (action == Action.PLAY) view.showResult(entry.session.play());
                else if (action == Action.EXPORT) {
                    Path folder = view.chooseExportDirectory(entry.getFilename());
                    if (folder == null) continue;
                    Path destination = folder.resolve(entry.getFilename());
                    if (!Files.exists(destination) || view.confirmOverwrite(destination))
                        entry.session.save(destination);
                }
            } catch (IOException | IllegalArgumentException | IllegalStateException error) {
                if (!report(error.getMessage(), error)) return;
            }
        }
    }

    private boolean importLevel(final Path source) {
        try {
            Path identity = source.toAbsolutePath().normalize();
            CustomLevelSession candidate = sessions.get();
            candidate.load(identity);
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).source.equals(identity)) {
                    entries.set(i, new Entry(identity, candidate));
                    selected = i;
                    return true;
                }
            }
            entries.add(new Entry(identity, candidate));
            selected = entries.size() - 1;
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            return report(source.getFileName() + ": " + error.getMessage(), error);
        }
    }

    /** If even the error dialog fails, leave this tool and let Core reopen the mode menu. */
    private boolean report(final String message, final Exception error) {
        LOGGER.log(java.util.logging.Level.WARNING, "Custom-level action failed", error);
        if (Thread.currentThread().isInterrupted()) return false;
        try {
            view.showError(message == null ? "Unable to complete the action." : message);
            return true;
        } catch (IllegalArgumentException | IllegalStateException dialogError) {
            LOGGER.log(java.util.logging.Level.WARNING,
                    "Unable to show the error dialog; returning to the mode menu", dialogError);
            return false;
        }
    }
}
