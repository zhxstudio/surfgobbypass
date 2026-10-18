// zhxstudio — Android shell output handling, shared by Root and Shizuku.
package com.zhx.studiotool;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShellReplies {
    private ShellReplies() {}
    static boolean accepted(int code, String output) {
        if (code != 0) return false;
        String text = output.toLowerCase(Locale.ROOT);
        return !Pattern.compile("(?m)^\\s*(error\\b|exception\\b|failure\\b)").matcher(text).find()
                && !Pattern.compile("\\b[\\w.$]*exception\\b").matcher(text).find() && !text.contains("permission denial")
                && !text.contains("permission denied") && !text.contains("unable to resolve")
                && !text.contains("status: timeout") && !text.contains("being kept for the user")
                && !text.contains("should be handled by the caller");
    }
    static String component(String output, String targetPackage) {
        Matcher matcher = Pattern.compile("(?m)^\\s*(" + Pattern.quote(targetPackage)
                + "/[A-Za-z0-9_.$]+)\\s*$").matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }
    static String failure(String stage, int code, String output) {
        return stage + ": exit=" + code + "\n" + output.trim();
    }
}
