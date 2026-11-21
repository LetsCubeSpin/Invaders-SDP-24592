package engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Development-only supervisor. Every battle runs in a fresh child JVM. */
public final class StoryDebugController {
    enum Action { REPLAY, CHANGE, EXIT }

    record Result(int exitCode, String message) { }

    interface View {
        StoryDebugSelection choose(StoryDebugSelection previous) throws Exception;
        Action afterStage(StoryDebugSelection selection, Result result) throws Exception;
    }

    interface StageRunner {
        Result run(StoryDebugSelection selection) throws IOException, InterruptedException;
    }

    private StoryDebugController() { }

    public static void main(String[] args) throws Exception {
        // Preserve the existing four-argument, one-stage entry point and its exit status.
        if (args.length != 0) {
            StoryDebugLauncher.main(args);
            return;
        }
        StoryDebugLauncher.requireDebugSession();
        View view = new View() {
            public StoryDebugSelection choose(StoryDebugSelection previous) throws Exception {
                return onEdt(() -> StoryDebugLauncher.choose(previous));
            }

            public Action afterStage(StoryDebugSelection selection, Result result) throws Exception {
                return onEdt(() -> {
                    Object[] buttons = {"Replay same settings", "Change selection", "Exit tool"};
                    int answer = JOptionPane.showOptionDialog(null,
                            result.message() + "\n\n" + selection.describe(), "LAST LINE | Story debug finished",
                            JOptionPane.DEFAULT_OPTION,
                            result.exitCode() == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE,
                            null, buttons, buttons[0]);
                    return answer == 0 ? Action.REPLAY : answer == 1 ? Action.CHANGE : Action.EXIT;
                });
            }
        };
        int status = runLoop(view, selection -> {
            try (StoryDebugSession session = new StoryDebugSession(Path.of(System.getProperty("user.home")))) {
                int code = session.run(selection);
                return new Result(code, code == 0
                        ? "Stage ended (clear, defeat, or window closed). Choose what to do next."
                        : "Stage failed with exit code " + code + ". See the terminal for details.");
            }
        }, StoryDebugLauncher.defaultSelection());
        if (status != 0) System.exit(status);
    }

    static int runLoop(View view, StageRunner runner, StoryDebugSelection initial) throws Exception {
        StoryDebugSelection selection = view.choose(initial);
        if (selection == null) return 0;
        while (true) {
            Result result;
            try {
                result = runner.run(selection);
            } catch (IOException failure) {
                result = new Result(1, "Could not run or clean up the stage: " + failure.getMessage());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return 130;
            }
            Action action = view.afterStage(selection, result);
            if (action == Action.EXIT) return result.exitCode();
            if (action == Action.CHANGE) {
                selection = view.choose(selection);
                if (selection == null) return result.exitCode();
            }
        }
    }

    private static <T> T onEdt(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
