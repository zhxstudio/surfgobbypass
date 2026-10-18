// zhxstudio — regression tests for Android launch-result variants.
package com.zhx.studiotool;

public final class ShellRepliesTest {
    private static int checks;
    public static void main(String[] args) {
        check(ShellReplies.accepted(0, "Starting: Intent { cmp=com.KotGames.SurfGO/.Main }"), "standard start");
        check(ShellReplies.accepted(0, ""), "successful shell with suppressed output");
        check(ShellReplies.accepted(0, "Warning: Activity not started, its current task has been brought to the front"), "existing task");
        check(ShellReplies.accepted(0, "Warning: Activity not started, intent has been delivered to currently running top-most instance."), "existing activity");
        check(!ShellReplies.accepted(0, "Starting: Intent {}\nError: Activity not started, unable to resolve Intent"), "error even when exit is zero");
        check(!ShellReplies.accepted(0, "Error type 3"), "missing activity");
        check(!ShellReplies.accepted(0, "java.lang.SecurityException: access denied"), "permission exception");
        check(!ShellReplies.accepted(0, "java.lang.IllegalArgumentException: invalid intent"), "other exception");
        check(!ShellReplies.accepted(0, "Permission Denial: startActivity"), "permission denial");
        check(!ShellReplies.accepted(127, "sh: am: not found"), "missing command");
        check(!ShellReplies.accepted(0, "Warning: Activity not started because the current activity is being kept for the user."), "deferred app switch");
        check(!ShellReplies.accepted(0, "Status: timeout"), "launch timeout");
        String pkg = "com.KotGames.SurfGO";
        check((pkg + "/.Main").equals(ShellReplies.component("priority=0\n" + pkg + "/.Main\n", pkg)), "relative launcher");
        check((pkg + "/com.unity3d.player.UnityPlayerActivity").equals(ShellReplies.component(pkg + "/com.unity3d.player.UnityPlayerActivity", pkg)), "external class namespace");
        check(ShellReplies.component("com.other/.Main", pkg) == null, "wrong package rejected");
        check(ShellReplies.component("No activity found", pkg) == null, "no activity");
        check(ShellReplies.component(pkg + "/.Main;reboot", pkg) == null, "invalid component rejected");
        System.out.println(checks + " launch-response checks passed");
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
