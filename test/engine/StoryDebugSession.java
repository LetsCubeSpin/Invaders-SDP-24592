package engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Owns one battle process and its temporary working, home, storage and temp directory. */
final class StoryDebugSession implements AutoCloseable {
    private static final int WINDOWS_DELETE_RETRIES = 20;
    private static final int DELETE_RETRY_MILLIS = 50;

    private final Path directory;
    private final Thread shutdown;
    private Process process;
    private boolean closed;

    StoryDebugSession(Path root) throws IOException {
        directory = Files.createTempDirectory(root.toAbsolutePath(), "stage-");
        shutdown = new Thread(() -> {
            try { close(); }
            catch (IOException failure) { System.err.println("Could not clean debug stage: " + failure); }
        }, "story-debug-stage-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdown);
    }

    Path directory() { return directory; }

    List<String> command(StoryDebugSelection selection) {
        String executable = File.separatorChar == '\\' ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Dinvaders.storyDebug=true",
                "-Djava.util.prefs.PreferencesFactory=shop.PreviewPreferencesFactory",
                "-Duser.home=" + directory, "-Dinvaders.storageDir=" + directory,
                "-Djava.io.tmpdir=" + directory, "-Dfile.encoding=UTF-8",
                "-Djava.util.logging.config.class=" + DevelopmentLogging.class.getName(),
                "-cp", childClasspath(directory), StoryDebugLauncher.Battle.class.getName()));
        command.addAll(selection.arguments());
        return command;
    }

    static String childClasspath(Path directory) {
        List<String> entries = new ArrayList<>();
        String classpath = System.getProperty("invaders.test.classpath", System.getProperty("java.class.path"));
        for (String entry : classpath.split(Pattern.quote(File.pathSeparator), -1)) {
            Path absolute = Path.of(entry).toAbsolutePath().normalize();
            // Unix scripts use classes:resources relative to the supervisor. Keep those paths
            // relative in the child too, so ':' in a checkout/TMPDIR name remains supported.
            try { entries.add(directory.toAbsolutePath().normalize().relativize(absolute).toString()); }
            catch (IllegalArgumentException differentDrive) { entries.add(absolute.toString()); }
        }
        return String.join(File.pathSeparator, entries);
    }

    int run(StoryDebugSelection selection) throws IOException, InterruptedException {
        return runCommand(command(selection), ProcessBuilder.Redirect.INHERIT);
    }

    int runCommand(List<String> command, ProcessBuilder.Redirect output) throws IOException, InterruptedException {
        Process running;
        synchronized (this) {
            if (closed || process != null) throw new IllegalStateException("A stage session runs only once.");
            process = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectErrorStream(true).redirectOutput(output).start();
            running = process;
        }
        return running.waitFor();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        boolean interrupted = false;
        try {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                // Finish stopping our child even if the controller's wait was interrupted.
                while (process.isAlive()) {
                    try { process.waitFor(); }
                    catch (InterruptedException interruption) { interrupted = true; }
                }
            }
            for (int retries = 0; ; retries++) {
                try {
                    deleteStageDirectory();
                    break;
                } catch (FileSystemException failure) {
                    // Windows reports a sharing violation as a plain FileSystemException
                    // with localized text. Typed errors such as a missing path propagate.
                    if (File.separatorChar != '\\' || failure.getClass() != FileSystemException.class
                            || retries == WINDOWS_DELETE_RETRIES) throw failure;
                    try { Thread.sleep(DELETE_RETRY_MILLIS); }
                    catch (InterruptedException interruption) { interrupted = true; }
                }
            }
            closed = true;
            // If cleanup failed, the registered hook can still retry at supervisor exit.
            try { Runtime.getRuntime().removeShutdownHook(shutdown); }
            catch (IllegalStateException shuttingDown) { /* This hook is already executing. */ }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void deleteStageDirectory() throws IOException {
        // Start each attempt at this session's own root. Never follow links inside it.
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
