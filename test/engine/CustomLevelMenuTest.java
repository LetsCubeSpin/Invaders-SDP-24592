package engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import level.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import engine.CustomLevelMenu.Action;

class CustomLevelMenuTest {
    @TempDir Path directory;
    private final List<String> played = new ArrayList<>();

    private CustomLevelSession session() {
        return new CustomLevelSession(448, 520, new JsonLevelReader(), new JsonLevelWriter(),
                () -> 1.25, level -> {
                    played.add(level.getName());
                    return new GameState(1, 100, 3, 1, 1);
                });
    }

    private Path source(String filename, String title) throws IOException {
        Path path = directory.resolve(filename);
        Files.createDirectories(path.getParent());
        new JsonLevelWriter().write(path, new LevelDefinition(1, "test", title,
                Collections.singletonList(new WaveDefinition("wave", new EnemyCell[][] {
                    {new EnemyCell("A", 1)}
                }, 40, 100, 60, 2000))));
        return path;
    }

    private void run(StubView view) { new CustomLevelMenu(this::session, view).run(); }

    @Test
    void importsSeveralLevelsAndPlaysTheChosenOne() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.PLAY, Action.BACK);
        view.batches.add(List.of(source("first.json", "First"), source("second.json", "Second")));
        view.choices.add(0);
        run(view);
        assertEquals(List.of("First"), played);
        assertEquals(2, view.entries.size());
        assertEquals(0, view.selected);
        assertEquals(1, view.results);
    }

    @Test
    void exportSelectsFromLibraryAndUsesTheImportedFilenameNotTheJsonTitle() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.EXPORT, Action.BACK);
        Path first = source("first.json", "Different internal title");
        view.batches.add(List.of(first, source("second.json", "Second")));
        view.choices.add(0);
        view.output = Files.createDirectory(directory.resolve("exports"));
        run(view);
        assertEquals("first.json", view.exportName);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(view.output.resolve("first.json")));
        assertFalse(Files.exists(view.output.resolve("Different internal title.json")));
        assertFalse(Files.exists(view.output.resolve("second.json")));
    }

    @Test
    void equalFilenamesFromDifferentFoldersRemainSeparate() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.PLAY, Action.PLAY, Action.BACK);
        view.batches.add(List.of(source("one/same.json", "First"), source("two/same.json", "Second")));
        view.choices.addAll(List.of(1, 0));
        run(view);
        assertEquals(List.of("Second", "First"), played);
        assertEquals(2, view.entries.size());
        assertNotEquals(view.entries.get(0).getSource(), view.entries.get(1).getSource());
    }

    @Test
    void reimportingTheSameSourceRefreshesWithoutDuplicatingIt() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.IMPORT, Action.BACK);
        Path file = source("same.json", "Original");
        view.batches.add(List.of(file));
        view.batches.add(List.of(file.getParent().resolve("./same.json")));
        run(view);
        assertEquals(1, view.entries.size());
    }

    @Test
    void invalidFilesDoNotRemoveValidImportsInTheSameBatch() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.PLAY, Action.BACK);
        Path broken = directory.resolve("broken.json");
        Files.writeString(broken, "{ invalid");
        view.batches.add(List.of(source("good.json", "Good"), broken));
        run(view);
        assertEquals(1, view.errors);
        assertEquals(1, view.entries.size());
        assertEquals(List.of("Good"), played);
    }

    @Test
    void failedRefreshKeepsTheOldDefinition() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.IMPORT, Action.PLAY, Action.BACK);
        Path file = source("same.json", "Original");
        view.batches.add(List.of(file));
        view.batches.add(List.of(file));
        view.beforeSecondImport = () -> {
            try { Files.writeString(file, "invalid"); }
            catch (IOException e) { throw new RuntimeException(e); }
        };
        run(view);
        assertEquals(List.of("Original"), played);
        assertEquals(1, view.errors);
        assertEquals(1, view.entries.size());
    }

    @Test
    void cancellingListSelectionNeverPlaysOrOpensExportDialog() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.PLAY, Action.EXPORT, Action.BACK);
        view.batches.add(List.of(source("file.json", "Title")));
        view.choices.addAll(List.of(-1, -1));
        run(view);
        assertTrue(played.isEmpty());
        assertNull(view.exportName);
    }

    @Test
    void cancellingImportAndExportFolderKeepsTheLibrary() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.IMPORT, Action.EXPORT, Action.BACK);
        view.batches.add(List.of(source("file.json", "Title")));
        view.batches.add(Collections.emptyList());
        run(view);
        assertEquals(1, view.entries.size());
        assertEquals(0, view.errors);
    }

    @Test
    void refusingOverwritePreservesTheOriginalFile() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.EXPORT, Action.BACK);
        view.batches.add(List.of(source("file.json", "Title")));
        view.output = Files.createDirectory(directory.resolve("exports"));
        Path existing = view.output.resolve("file.json");
        Files.writeString(existing, "original");
        run(view);
        assertEquals("original", Files.readString(existing));
        assertEquals(1, view.confirmations);
    }

    @Test
    void approvingOverwriteUsesTheOriginalUnicodeFilename() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.EXPORT, Action.BACK);
        Path file = source("한글 이름.JSON", "Not the filename");
        view.batches.add(List.of(file));
        view.output = Files.createDirectory(directory.resolve("exports"));
        Path existing = view.output.resolve(file.getFileName());
        Files.writeString(existing, "old");
        view.overwrite = true;
        run(view);
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(existing));
        assertEquals(1, view.confirmations);
    }

    @Test
    void exportFailureKeepsTheLibraryPlayable() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.EXPORT, Action.PLAY, Action.BACK);
        view.batches.add(List.of(source("file.json", "Title")));
        view.output = directory.resolve("missing-folder");
        run(view);
        assertEquals(1, view.errors);
        assertEquals(List.of("Title"), played);
    }

    @Test
    void emptyLibraryNeverOffersASelectionOrStartsPlay() {
        StubView view = new StubView(Action.PLAY, Action.EXPORT, Action.BACK);
        run(view);
        assertTrue(played.isEmpty());
        assertNull(view.exportName);
        assertEquals(0, view.listVisits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"chooseAction", "chooseImports", "chooseLevel",
            "chooseExportDirectory", "confirmOverwrite", "showResult"})
    void dialogFailuresAreReportedAndReturnToTheCustomMenu(String point) throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.IMPORT, Action.EXPORT, Action.PLAY, Action.BACK);
        Path file = source("file.json", "Title");
        view.batches.add(List.of(file));
        view.batches.add(List.of(file));
        view.output = Files.createDirectory(directory.resolve("exports"));
        Files.writeString(view.output.resolve("file.json"), "keep existing");
        view.failAt = point;
        assertDoesNotThrow(() -> run(view));
        assertEquals(1, view.errors);
        assertEquals(1, view.entries.size());
        assertEquals("keep existing", Files.readString(view.output.resolve("file.json")));
    }

    @Test
    void failedErrorDialogExitsOnlyTheCustomMenuAndKeepsCompletedImports() throws Exception {
        StubView view = new StubView(Action.IMPORT, Action.BACK);
        Path broken = directory.resolve("broken.json");
        Files.writeString(broken, "invalid");
        view.batches.add(List.of(source("good.json", "Good"), broken,
                source("not-yet-imported.json", "Later")));
        view.errorDialogFails = true;
        CustomLevelMenu menu = new CustomLevelMenu(this::session, view);
        assertDoesNotThrow(menu::run);
        assertEquals(1, view.actions.size(), "Return to Core rather than retrying a broken error dialog");
        assertDoesNotThrow(menu::run);
        assertEquals(1, view.entries.size());
        assertEquals("good.json", view.entries.get(0).getFilename());
    }

    @Test
    void failedImportChooserAndFailedErrorDialogDoNotEscapeToCore() {
        StubView view = new StubView(Action.IMPORT, Action.BACK);
        view.failAt = "chooseImports";
        view.errorDialogFails = true;
        assertDoesNotThrow(() -> run(view));
        assertEquals(1, view.actions.size());
    }

    @Test
    void interruptedDialogStopsWithoutOpeningAnotherDialogAndPreservesInterrupt() {
        StubView view = new StubView(Action.IMPORT, Action.BACK);
        view.failAt = "chooseImports";
        view.interruptOnFailure = true;
        try {
            assertDoesNotThrow(() -> run(view));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, view.errors);
            assertEquals(1, view.actions.size());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void exportSerializesTheImportedSnapshotNotOriginalFileBytes() throws Exception {
        Path file = source("snapshot.json", "Imported title");
        // External formatting differs from the writer's canonical output.
        String externalJson = Files.readString(file).replace("\n", "").replace("  ", "");
        Files.writeString(file, externalJson);
        StubView view = new StubView(Action.IMPORT, Action.IMPORT, Action.EXPORT, Action.BACK);
        view.batches.add(List.of(file));
        view.batches.add(Collections.emptyList());
        view.beforeSecondImport = () -> {
            try { Files.writeString(file, "Changed on disk after Import"); }
            catch (IOException e) { throw new RuntimeException(e); }
        };
        view.output = Files.createDirectory(directory.resolve("exports"));
        run(view);
        Path exported = view.output.resolve("snapshot.json");
        assertNotEquals(externalJson, Files.readString(exported));
        assertEquals("Imported title", new JsonLevelReader().read(exported).getName());
        assertEquals("Changed on disk after Import", Files.readString(file));
    }

    private static final class StubView implements CustomLevelMenu.View {
        private final ArrayDeque<Action> actions;
        private final List<List<Path>> batches = new ArrayList<>();
        private final ArrayDeque<Integer> choices = new ArrayDeque<>();
        private int batch;
        private Path output;
        private int errors, results, confirmations, selected, listVisits;
        private boolean overwrite;
        private String exportName;
        private List<CustomLevelMenu.Entry> entries;
        private Runnable beforeSecondImport;
        private String failAt;
        private boolean errorDialogFails;
        private boolean interruptOnFailure;

        private void failOnce(String point) {
            if (point.equals(failAt)) {
                failAt = null;
                if (interruptOnFailure) Thread.currentThread().interrupt();
                throw new IllegalStateException("Simulated dialog failure: " + point);
            }
        }

        StubView(Action... actions) { this.actions = new ArrayDeque<>(Arrays.asList(actions)); }
        public Action chooseAction(List<CustomLevelMenu.Entry> entries, int selected) {
            failOnce("chooseAction");
            this.entries = entries;
            this.selected = selected;
            return actions.removeFirst();
        }
        public List<Path> chooseImports() {
            failOnce("chooseImports");
            if (batch == 1 && beforeSecondImport != null) beforeSecondImport.run();
            return batches.get(batch++);
        }
        public int chooseLevel(List<CustomLevelMenu.Entry> entries, int selected, Action action) {
            failOnce("chooseLevel");
            listVisits++;
            return choices.isEmpty() ? 0 : choices.removeFirst();
        }
        public Path chooseExportDirectory(String filename) { failOnce("chooseExportDirectory"); exportName = filename; return output; }
        public boolean confirmOverwrite(Path path) { failOnce("confirmOverwrite"); confirmations++; return overwrite; }
        public void showError(String message) {
            errors++;
            if (errorDialogFails) throw new IllegalStateException("Error dialog unavailable");
        }
        public void showResult(GameState result) { failOnce("showResult"); results++; }
    }
}
