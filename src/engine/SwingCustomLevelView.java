package engine;

import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import screen.CustomLevelListScreen;
import screen.CustomLevelScreen;

/** Native file dialogs on the EDT; menus and gameplay stay on the controller thread. */
final class SwingCustomLevelView implements CustomLevelMenu.View {
    private final Frame frame;
    private final int fps;

    SwingCustomLevelView(final Frame frame, final int fps) {
        this.frame = frame;
        this.fps = fps;
    }

    public CustomLevelMenu.Action chooseAction(final List<CustomLevelMenu.Entry> entries,
            final int selected) {
        CustomLevelScreen screen = new CustomLevelScreen(frame.getWidth(), frame.getHeight(), fps,
                entries.size());
        frame.setScreen(screen);
        return screen.getAction();
    }

    public int chooseLevel(final List<CustomLevelMenu.Entry> entries, final int selected,
            final CustomLevelMenu.Action action) {
        CustomLevelListScreen screen = new CustomLevelListScreen(frame.getWidth(), frame.getHeight(),
                fps, entries, selected, action);
        frame.setScreen(screen);
        return screen.getSelection();
    }

    public List<Path> chooseImports() {
        return onEdt(() -> {
            JFileChooser chooser = new JFileChooser(new java.io.File("examples/levels"));
            chooser.setFileFilter(new FileNameExtensionFilter("JSON levels (*.json)", "json"));
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setMultiSelectionEnabled(true);
            chooser.setDialogTitle("Import JSON levels");
            if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION)
                return Collections.emptyList();
            return Arrays.asList(chooser.getSelectedFiles()).stream()
                    .map(java.io.File::toPath).collect(java.util.stream.Collectors.toList());
        });
    }

    public Path chooseExportDirectory(final String filename) {
        return onEdt(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Export " + filename + " — select folder");
            chooser.setApproveButtonText("Export here");
            return chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION
                    ? chooser.getSelectedFile().toPath() : null;
        });
    }

    public boolean confirmOverwrite(final Path destination) {
        return onEdt(() -> JOptionPane.showConfirmDialog(frame,
                "Replace this file?\n" + destination, "Export custom level",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION);
    }

    public void showError(final String message) {
        onEdt(() -> { JOptionPane.showMessageDialog(frame, message, "Custom level",
                JOptionPane.ERROR_MESSAGE); return null; });
    }

    public void showResult(final GameState result) {
        onEdt(() -> { JOptionPane.showMessageDialog(frame,
                "Score: " + result.getScore() + "\nLives: " + result.getLivesRemaining()
                + "\nCustom play does not update official records.", "Custom level result",
                JOptionPane.INFORMATION_MESSAGE); return null; });
    }

    private <T> T onEdt(final Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(() -> {
            try {
                return action.call();
            } finally {
                Core.getInputManager().releaseAllInputs();
                frame.requestFocusInWindow();
            }
        });
        try {
            if (SwingUtilities.isEventDispatchThread()) task.run();
            else SwingUtilities.invokeAndWait(task);
            return task.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("File dialog interrupted.", error);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to display the custom-level dialog.", error);
        }
    }
}
