// zhxstudio — surfgobbypass: persistent privileged shell, no sleep and no force-stop.
package com.zhx.studiotool;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

public final class ShellRunner {
    public static final String TARGET_PACKAGE = "com.KotGames.SurfGO";
    private static final Object LOCK = new Object();
    private static volatile Session session;
    private ShellRunner() {}

    public static String prepare(boolean root) {
        synchronized (LOCK) {
            try { return resolveLauncher(ensureSession(root)); }
            catch (InterruptedException e) { close(); Thread.currentThread().interrupt(); return "CANCELLED"; }
            catch (Exception e) { close(); return e.toString(); }
        }
    }
    public static String run(boolean root) {
        synchronized (LOCK) {
            try {
                Session shell = ensureSession(root);
                String resolutionError = resolveLauncher(shell);
                if (!resolutionError.isEmpty()) return resolutionError;
                Result home = shell.command("input keyevent 3", 5000);
                if (!ShellReplies.accepted(home.code, home.output)) {
                    Result fallback = shell.command("am start --user current -a android.intent.action.MAIN"
                            + " -c android.intent.category.HOME", 5000);
                    if (!ShellReplies.accepted(fallback.code, fallback.output)) {
                        return ShellReplies.failure("HOME_FAILED", home.code, home.output)
                                + "\n" + ShellReplies.failure("HOME_FALLBACK", fallback.code, fallback.output);
                    }
                }
                Result result = shell.command("am start --user current -a android.intent.action.MAIN"
                        + " -c android.intent.category.LAUNCHER -f 0x10200000 -n '"
                        + shell.launchComponent + "'", 10000);
                if (ShellReplies.accepted(result.code, result.output)) return "";
                shell.launchComponent = null;
                return ShellReplies.failure("LAUNCH_FAILED", result.code, result.output);
            } catch (TimeoutException e) {
                close(); return "TIMEOUT";
            } catch (InterruptedException e) {
                close(); Thread.currentThread().interrupt(); return "CANCELLED";
            } catch (Exception e) { close(); return e.toString(); }
        }
    }
    private static String resolveLauncher(Session shell) throws Exception {
        if (shell.launchComponent != null) return "";
        String query = " resolve-activity --brief --user current -a android.intent.action.MAIN"
                + " -c android.intent.category.LAUNCHER -p " + TARGET_PACKAGE;
        Result result = shell.command("cmd package" + query, 10000);
        String component = result.code == 0 ? ShellReplies.component(result.output, TARGET_PACKAGE) : null;
        if (component == null) {
            Result fallback = shell.command("pm" + query, 10000);
            component = fallback.code == 0 ? ShellReplies.component(fallback.output, TARGET_PACKAGE) : null;
            if (component == null) return "TARGET_NOT_FOUND: " + TARGET_PACKAGE + "\n"
                    + ShellReplies.failure("RESOLVE", result.code, result.output) + "\n"
                    + ShellReplies.failure("RESOLVE_FALLBACK", fallback.code, fallback.output);
        }
        shell.launchComponent = component;
        return "";
    }
    private static Session ensureSession(boolean root) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Session current = session;
        if (current != null && current.root == root && current.process.isAlive()) return current;
        close();
        current = new Session(root);
        session = current;
        Result identity = current.command("id -u", 60000);
        String uid = identity.output.trim();
        if (identity.code != 0 || (root ? !uid.equals("0") : !(uid.equals("0") || uid.equals("2000")))) {
            throw new IOException("Privileged shell access denied");
        }
        return current;
    }
    public static void close() {
        Session current = session;
        session = null;
        if (current != null) current.close();
    }
    private static final class Result {
        final int code;
        final String output;
        Result(int code, String output) { this.code = code; this.output = output; }
    }
    private static final class Session {
        final boolean root;
        final Process process;
        final Writer writer;
        final BlockingQueue<String> lines = new LinkedBlockingQueue<>(256);
        final String eof = "EOF_" + UUID.randomUUID();
        String launchComponent;
        Session(boolean root) throws IOException {
            this.root = root;
            process = new ProcessBuilder(root ? "su" : "sh").redirectErrorStream(true).start();
            writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            Thread drain = new Thread(() -> {
                try (BufferedReader input = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = input.readLine()) != null) {
                        if (line.length() > 32768 || !lines.offer(line)) { close(); break; }
                    }
                } catch (IOException ignored) { }
                finally { lines.offer(eof); }
            }, "zhxstudio-shell");
            drain.setDaemon(true);
            drain.start();
        }
        Result command(String script, long timeout) throws Exception {
            String marker = "ZHX_" + UUID.randomUUID().toString().replace("-", "") + ":";
            writer.write(script + "\nzhx_rc=$?; printf '\\n" + marker + "%s\\n' \"$zhx_rc\"\n");
            writer.flush();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
            StringBuilder output = new StringBuilder();
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException();
                String line = lines.poll(remaining, TimeUnit.NANOSECONDS);
                if (line == null) throw new TimeoutException();
                if (line.equals(eof)) throw new IOException("Shell closed");
                if (line.startsWith(marker)) return new Result(
                        Integer.parseInt(line.substring(marker.length()).trim()), output.toString());
                output.append(line).append('\n');
                if (output.length() > 32768) throw new IOException("Shell output limit exceeded");
            }
        }
        void close() { process.destroyForcibly(); lines.clear(); lines.offer(eof); }
    }
}
