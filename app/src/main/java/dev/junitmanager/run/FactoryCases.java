package dev.junitmanager.run;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code @TestFactory} メソッド本体の {@code dynamicTest} 呼び出しを、ソースに書かれた順で読む。
 */
public final class FactoryCases {
    private FactoryCases() {
    }

    public record Call(String displayName, String typeName, String methodName) {
    }

    public static List<Call> read(String source, int line) {
        if (source == null || source.isEmpty() || line < 1) {
            return List.of();
        }
        int start = offsetOfLine(source, line);
        int open = indexOfCode(source, '{', start);
        if (open < 0) {
            return List.of();
        }
        int close = matchingBrace(source, open);
        if (close < 0) {
            return List.of();
        }
        return calls(source, open + 1, close);
    }

    public static String displayName(List<Call> calls, String methodName, String reportedName) {
        int index = index(methodName, reportedName);
        if (calls == null || index < 1 || index > calls.size()) {
            return null;
        }
        String display = calls.get(index - 1).displayName();
        return display == null || display.isBlank() ? null : display;
    }

    public static int index(String methodName, String reportedName) {
        if (methodName == null || methodName.isBlank() || reportedName == null) {
            return -1;
        }
        Matcher matcher = Pattern.compile("^" + Pattern.quote(methodName) + "\\(\\)\\[(\\d+)\\].*").matcher(reportedName);
        if (!matcher.matches()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static List<Call> calls(String source, int from, int to) {
        List<Call> found = new ArrayList<>();
        int i = from;
        while (i < to) {
            int skip = skipNoise(source, i);
            if (skip > i) {
                i = skip;
                continue;
            }
            if (identifierAt(source, i, "dynamicTest")) {
                int after = i + "dynamicTest".length();
                int paren = indexOfCode(source, '(', after);
                if (paren >= 0 && paren < to && blankBetween(source, after, paren)) {
                    int end = matchingParen(source, paren);
                    if (end > paren) {
                        found.add(call(source, paren + 1, end));
                        i = end + 1;
                        continue;
                    }
                }
            }
            i++;
        }
        return List.copyOf(found);
    }

    private static Call call(String source, int from, int to) {
        String display = null;
        String type = null;
        String method = null;
        int i = from;
        while (i < to) {
            if (source.charAt(i) == '"') {
                if (display == null) {
                    display = readString(source, i);
                }
                i = endOfString(source, i);
                continue;
            }
            if (source.charAt(i) == '\'') {
                i = endOfString(source, i);
                continue;
            }
            int skip = skipNoise(source, i);
            if (skip > i) {
                i = skip;
                continue;
            }
            if (i + 1 < to && source.charAt(i) == ':' && source.charAt(i + 1) == ':' && method == null) {
                type = typeBefore(source, from, i);
                method = identifierAfter(source, i + 2, to);
                i += 2;
                continue;
            }
            i++;
        }
        return new Call(display, type, method);
    }

    private static String typeBefore(String source, int limit, int colon) {
        int i = colon - 1;
        while (i >= limit && Character.isWhitespace(source.charAt(i))) {
            i--;
        }
        int end = i + 1;
        while (i >= limit) {
            char c = source.charAt(i);
            if (Character.isJavaIdentifierPart(c) || c == '.') {
                i--;
                continue;
            }
            break;
        }
        int start = i + 1;
        if (start >= end) {
            return null;
        }
        return source.substring(start, end);
    }

    private static String identifierAfter(String source, int from, int to) {
        int i = from;
        while (i < to && Character.isWhitespace(source.charAt(i))) {
            i++;
        }
        if (i >= to || !Character.isJavaIdentifierStart(source.charAt(i))) {
            return null;
        }
        int start = i++;
        while (i < to && Character.isJavaIdentifierPart(source.charAt(i))) {
            i++;
        }
        return source.substring(start, i);
    }

    private static boolean identifierAt(String source, int index, String word) {
        if (!source.startsWith(word, index)) {
            return false;
        }
        if (index > 0 && Character.isJavaIdentifierPart(source.charAt(index - 1))) {
            return false;
        }
        int end = index + word.length();
        return end >= source.length() || !Character.isJavaIdentifierPart(source.charAt(end));
    }

    private static boolean blankBetween(String source, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int offsetOfLine(String source, int line) {
        int current = 1;
        for (int i = 0; i < source.length(); i++) {
            if (current == line) {
                return i;
            }
            if (source.charAt(i) == '\n') {
                current++;
            }
        }
        return source.length();
    }

    private static int indexOfCode(String source, char wanted, int from) {
        for (int i = from; i < source.length(); i++) {
            int skip = skipNoise(source, i);
            if (skip > i) {
                i = skip - 1;
                continue;
            }
            if (source.charAt(i) == wanted) {
                return i;
            }
        }
        return -1;
    }

    private static int matchingBrace(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            int skip = skipNoise(source, i);
            if (skip > i) {
                i = skip - 1;
                continue;
            }
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int matchingParen(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            int skip = skipNoise(source, i);
            if (skip > i) {
                i = skip - 1;
                continue;
            }
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipNoise(String source, int index) {
        if (index >= source.length()) {
            return index;
        }
        char c = source.charAt(index);
        if (c == '"' || c == '\'') {
            return endOfString(source, index);
        }
        if (c == '/' && index + 1 < source.length()) {
            char next = source.charAt(index + 1);
            if (next == '/') {
                int end = source.indexOf('\n', index + 2);
                return end < 0 ? source.length() : end;
            }
            if (next == '*') {
                int end = source.indexOf("*/", index + 2);
                return end < 0 ? source.length() : end + 2;
            }
        }
        return index;
    }

    private static int endOfString(String source, int quote) {
        char mark = source.charAt(quote);
        for (int i = quote + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == mark || c == '\n') {
                return i + 1;
            }
        }
        return source.length();
    }

    private static String readString(String source, int quote) {
        StringBuilder text = new StringBuilder();
        for (int i = quote + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < source.length()) {
                char next = source.charAt(++i);
                text.append(next == 'n' ? '\n' : next);
                continue;
            }
            if (c == '"' || c == '\n') {
                break;
            }
            text.append(c);
        }
        return text.toString();
    }
}
