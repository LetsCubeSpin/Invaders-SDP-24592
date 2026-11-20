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
    public enum Action { IMPORT, PLAY, EXPORT, BACK }

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
        while (true) {
            List<Entry> snapshot = Collections.unmodifiableList(new ArrayList<>(entries));
            Action action = view.chooseAction(snapshot, selected);
            if (action == Action.BACK) return;
            if (action == Action.IMPORT) {
                for (Path source : view.chooseImports()) importLevel(source);
                continue;
            }
            if (entries.isEmpty()) continue;
            int choice = view.chooseLevel(snapshot, selected, action);
            if (choice < 0) continue;
            if (choice >= entries.size()) throw new IllegalArgumentException("Unknown level selection");
            selected = choice;
            Entry entry = entries.get(choice);
            try {
                if (action == Action.PLAY) view.showResult(entry.session.play());
                else if (action == Action.EXPORT) {
                    Path folder = view.chooseExportDirectory(entry.getFilename());
                    if (folder == null) continue;
                    Path destination = folder.resolve(entry.getFilename());
                    if (!Files.exists(destination) || view.confirmOverwrite(destination))
                        entry.session.save(destination);
                }
            } catch (IOException | IllegalArgumentException | IllegalStateException error) {
                report(error);
            }
        }
    }

    private void importLevel(final Path source) {
        try {
            Path identity = source.toAbsolutePath().normalize();
            CustomLevelSession candidate = sessions.get();
            candidate.load(identity);
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).source.equals(identity)) {
                    entries.set(i, new Entry(identity, candidate));
                    selected = i;
                    return;
                }
            }
            entries.add(new Entry(identity, candidate));
            selected = entries.size() - 1;
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            view.showError(source.getFileName() + ": " + error.getMessage());
        }
    }

    private void report(final Exception error) {
        view.showError(error.getMessage() == null ? "Unable to complete the action." : error.getMessage());
    }
}
