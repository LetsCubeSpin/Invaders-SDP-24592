package engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import screen.GameScreen;

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
        StoryDebugLauncher.requireDebugSession();
        boolean singleStage = args.length != 0;
        StoryDebugSelection initial = singleStage ? StoryDebugSelection.parse(args) : StoryDebugLauncher.defaultSelection();
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
                if (code == GameScreen.RETURN_TO_STORY_DEBUG) return new Result(code, "Returned to settings.");
                return new Result(code, code == 0
                        ? "Stage ended (clear, defeat, or window closed). Choose what to do next."
                        : "Stage failed with exit code " + code + ". See the terminal for details.");
            }
        }, initial, singleStage);
        if (status != 0) System.exit(status);
    }

    static int runLoop(View view, StageRunner runner, StoryDebugSelection initial) throws Exception {
        return runLoop(view, runner, initial, false);
    }

    static int runLoop(View view, StageRunner runner, StoryDebugSelection initial, boolean singleStage) throws Exception {
        StoryDebugSelection selection = singleStage ? initial : view.choose(initial);
        if (selection == null) return 0;
        while (true) {
            Result result;
            try {
                result = runner.run(selection);
            } catch (IOException failure) {
                result = new Result(1, "Could not run or clean up the stage: " + failure.getMessage());
                if (singleStage) System.err.println(result.message());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return 130;
            }
            if (result.exitCode() == GameScreen.RETURN_TO_STORY_DEBUG) {
                // An explicit return opens the picker even when the first battle was started from the CLI.
                singleStage = false;
                selection = view.choose(selection);
                if (selection == null) return 0;
                continue;
            }
            if (singleStage) return result.exitCode();
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
