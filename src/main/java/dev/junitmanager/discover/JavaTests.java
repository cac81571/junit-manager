package dev.junitmanager.discover;

import dev.junitmanager.model.Framework;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavaTests {
    private static final Set<String> MODIFIERS = Set.of(
            "public", "protected", "private", "static", "final", "abstract", "default",
            "synchronized", "native", "strictfp", "sealed", "transient", "volatile"
    );
    private static final Set<String> NOT_METHODS = Set.of(
            "if", "for", "while", "switch", "catch", "try", "do", "return", "new",
            "throw", "else", "synchronized", "assert"
    );
    private static final Set<String> TEST_FQCN = Set.of(
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.api.TestFactory",
            "org.junit.jupiter.api.TestTemplate",
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.Test",
            "org.junit.experimental.theories.Theory"
    );
    private static final Set<String> SIMPLE_TESTS = Set.of(
            "Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate", "Theory"
    );
    private static final Set<String> JUPITER_SIMPLE = Set.of(
            "Test", "RepeatedTest", "TestFactory", "TestTemplate", "DisplayName", "Nested", "Disabled"
    );

    private JavaTests() {
    }

    static final class FileUnit {
        String packageName = "";
        final List<TypeUnit> types = new ArrayList<>();
    }

    static final class TypeUnit {
        String name;
        String displayName;
        boolean disabled;
        String ignoreReason;
        int line;
        final List<MethodUnit> methods = new ArrayList<>();
        final List<TypeUnit> nested = new ArrayList<>();
    }

    static final class MethodUnit {
        String name;
        String displayName;
        boolean disabled;
        String ignoreReason;
        int line;
        Framework framework = Framework.UNKNOWN;
    }

    static FileUnit parse(String source) {
        return new Parser(source).parse();
    }

    static boolean hasTests(TypeUnit type) {
        if (!type.methods.isEmpty()) {
            return true;
        }
        for (TypeUnit nested : type.nested) {
            if (hasTests(nested)) {
                return true;
            }
        }
        return false;
    }

    private static final class Parser {
        private final String s;
        private final int n;
        private int i;
        private final FileUnit file = new FileUnit();
        private final Imports imports = new Imports();

        private Parser(String source) {
            this.s = source == null ? "" : source;
            this.n = this.s.length();
        }

        private FileUnit parse() {
            parseMembers(null, 0);
            return file;
        }

        private void parseMembers(TypeUnit owner, int depth) {
            if (depth > 64) {
                return;
            }
            int guard = 0;
            while (!eof() && guard++ < n + 8) {
                int before = i;
                if (!parseOne(owner, depth)) {
                    return;
                }
                if (i == before) {
                    i++;
                }
            }
        }

        private boolean parseOne(TypeUnit owner, int depth) {
            skipWsAndComments();
            if (eof()) {
                return false;
            }
            if (ch() == '}') {
                i++;
                return owner == null;
            }
            int segment = i;
            List<Ann> annotations = new ArrayList<>();
            while (!eof()) {
                skipWsAndComments();
                if (eof()) {
                    return false;
                }
                if (ch() == '@') {
                    int save = i;
                    i++;
                    skipWsAndComments();
                    if (lookingAt("interface")) {
                        i += "interface".length();
                        addType(owner, finishType(annotations, owner, depth));
                        return true;
                    }
                    i = save;
                    annotations.add(readAnnotation());
                    continue;
                }
                if (consumeModifier()) {
                    continue;
                }
                break;
            }
            skipWsAndComments();
            if (eof()) {
                return false;
            }
            if (ch() == '{') {
                skipBlock();
                return true;
            }
            if (ch() == ';') {
                i++;
                return true;
            }
            if (owner == null && lookingAt("package")) {
                readPackage();
                return true;
            }
            if (owner == null && lookingAt("import")) {
                readImport();
                return true;
            }
            if (matchKeyword("class", "interface", "enum", "record") != null) {
                addType(owner, finishType(annotations, owner, depth));
                return true;
            }
            int before = i;
            readMember(segment, owner, annotations);
            if (i == before) {
                i++;
            }
            return true;
        }

        private void addType(TypeUnit owner, TypeUnit created) {
            if (created == null || created.name == null || created.name.isBlank()) {
                return;
            }
            if (owner == null) {
                file.types.add(created);
            } else {
                owner.nested.add(created);
            }
        }

        private TypeUnit finishType(List<Ann> annotations, TypeUnit owner, int depth) {
            skipWsAndComments();
            int nameAt = i;
            String name = readIdentifier();
            TypeUnit type = new TypeUnit();
            type.name = name.isEmpty() ? "" : name;
            type.line = lineOf(nameAt);
            type.displayName = textOf(annotations, this::isDisplayName);
            if (type.displayName == null || type.displayName.isBlank()) {
                type.displayName = type.name;
            }
            type.disabled = (owner != null && owner.disabled) || isDisabled(annotations);
            type.ignoreReason = textOf(annotations, this::isDisabledAnnotation);
            if ((type.ignoreReason == null || type.ignoreReason.isBlank()) && type.disabled && owner != null) {
                type.ignoreReason = owner.ignoreReason;
            }
            int paren = 0;
            while (!eof()) {
                char c = ch();
                if (c == '"' || c == '\'') {
                    skipLiteral();
                    continue;
                }
                if (isCommentStart()) {
                    skipComment();
                    continue;
                }
                if (c == '@') {
                    readAnnotation();
                    continue;
                }
                if (c == '(') {
                    paren++;
                    i++;
                    continue;
                }
                if (c == ')') {
                    if (paren > 0) {
                        paren--;
                    }
                    i++;
                    continue;
                }
                if (c == '{' && paren == 0) {
                    break;
                }
                if (c == ';' && paren == 0) {
                    break;
                }
                i++;
            }
            if (!eof() && ch() == '{') {
                if (type.name.isEmpty()) {
                    skipBlock();
                } else {
                    i++;
                    parseMembers(type, depth + 1);
                }
            }
            return type;
        }

        private void readMember(int segmentStart, TypeUnit owner, List<Ann> annotations) {
            Term term = scanHeader();
            MethodHit hit = findMethod(segmentStart, i, owner == null ? null : owner.name);
            if (term == Term.BRACE) {
                skipBlock();
            } else if (term == Term.SEMI) {
                i++;
            }
            if (hit == null || owner == null || !isJUnitTest(annotations)) {
                return;
            }
            MethodUnit method = new MethodUnit();
            method.name = hit.name;
            method.line = lineOf(hit.index);
            method.displayName = textOf(annotations, this::isDisplayName);
            if (method.displayName == null || method.displayName.isBlank()) {
                method.displayName = method.name;
            }
            boolean ownDisabled = isDisabled(annotations);
            method.disabled = ownDisabled || owner.disabled;
            method.ignoreReason = textOf(annotations, this::isDisabledAnnotation);
            if ((method.ignoreReason == null || method.ignoreReason.isBlank()) && method.disabled) {
                method.ignoreReason = owner.ignoreReason;
            }
            method.framework = frameworkOf(annotations);
            owner.methods.add(method);
        }

        private MethodHit findMethod(int start, int end, String className) {
            int depth = 0;
            int chosen = -1;
            int k = start;
            while (k < end) {
                char c = s.charAt(k);
                if (c == '"' || c == '\'') {
                    k = skipLiteralAt(k);
                    continue;
                }
                if (c == '/' && k + 1 < end && (s.charAt(k + 1) == '/' || s.charAt(k + 1) == '*')) {
                    k = skipCommentAt(k);
                    continue;
                }
                if (c == '(') {
                    if (depth == 0 && methodIdentBefore(start, k, className) != null) {
                        chosen = k;
                    }
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
                k++;
            }
            if (chosen < 0) {
                return null;
            }
            Ident ident = methodIdentBefore(start, chosen, className);
            if (ident == null) {
                return null;
            }
            return new MethodHit(ident.name, ident.index);
        }

        private Ident methodIdentBefore(int start, int paren, String className) {
            int j = paren - 1;
            while (j >= start && Character.isWhitespace(s.charAt(j))) {
                j--;
            }
            if (j < start || !Character.isJavaIdentifierPart(s.charAt(j))) {
                return null;
            }
            int end = j + 1;
            while (j >= start && Character.isJavaIdentifierPart(s.charAt(j))) {
                j--;
            }
            int begin = j + 1;
            int mark = begin - 1;
            while (mark >= start && Character.isWhitespace(s.charAt(mark))) {
                mark--;
            }
            if (mark >= start && s.charAt(mark) == '@') {
                return null;
            }
            String name = s.substring(begin, end);
            if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
                return null;
            }
            if (NOT_METHODS.contains(name) || name.equals(className)) {
                return null;
            }
            return new Ident(name, begin);
        }

        private Term scanHeader() {
            int paren = 0;
            int bracket = 0;
            while (!eof()) {
                char c = ch();
                if (c == '"' || c == '\'') {
                    skipLiteral();
                    continue;
                }
                if (isCommentStart()) {
                    skipComment();
                    continue;
                }
                if (c == '(') {
                    paren++;
                    i++;
                    continue;
                }
                if (c == ')') {
                    if (paren > 0) {
                        paren--;
                    }
                    i++;
                    continue;
                }
                if (c == '[') {
                    bracket++;
                    i++;
                    continue;
                }
                if (c == ']') {
                    if (bracket > 0) {
                        bracket--;
                    }
                    i++;
                    continue;
                }
                if (paren == 0 && bracket == 0 && c == '{') {
                    return Term.BRACE;
                }
                if (paren == 0 && bracket == 0 && c == ';') {
                    return Term.SEMI;
                }
                if (paren == 0 && bracket == 0 && c == '}') {
                    return Term.CLOSE;
                }
                i++;
            }
            return Term.EOF;
        }

        private void readPackage() {
            i += "package".length();
            skipWsAndComments();
            int start = i;
            while (!eof() && ch() != ';') {
                if (isCommentStart()) {
                    skipComment();
                    continue;
                }
                i++;
            }
            file.packageName = s.substring(start, Math.min(i, n)).replaceAll("\\s+", "");
            if (!eof() && ch() == ';') {
                i++;
            }
        }

        private void readImport() {
            i += "import".length();
            skipWsAndComments();
            boolean staticImport = false;
            if (lookingAt("static")) {
                staticImport = true;
                i += "static".length();
                skipWsAndComments();
            }
            String name = readDotted();
            skipWsAndComments();
            boolean star = false;
            if (!eof() && ch() == '.') {
                int save = i;
                i++;
                skipWsAndComments();
                if (!eof() && ch() == '*') {
                    star = true;
                    i++;
                } else {
                    i = save;
                }
            } else if (!eof() && ch() == '*') {
                star = true;
                i++;
            }
            skipUntilSemicolon();
            if (name.isEmpty()) {
                return;
            }
            if (star && !staticImport) {
                if (name.equals("org.junit.jupiter.api")) {
                    imports.starJupiter = true;
                }
                if (name.equals("org.junit")) {
                    imports.starJunit = true;
                }
            } else if (!star) {
                int dot = name.lastIndexOf('.');
                imports.types.put(dot < 0 ? name : name.substring(dot + 1), name);
            }
        }

        private boolean isJUnitTest(List<Ann> annotations) {
            for (Ann ann : annotations) {
                String resolved = imports.resolve(ann.name);
                if (resolved.startsWith("org.testng.")) {
                    continue;
                }
                if (TEST_FQCN.contains(resolved)) {
                    return true;
                }
                if (!resolved.contains(".") && SIMPLE_TESTS.contains(resolved)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isDisabled(List<Ann> annotations) {
            for (Ann ann : annotations) {
                if (isDisabledAnnotation(ann)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isDisabledAnnotation(Ann ann) {
            String resolved = imports.resolve(ann.name);
            return resolved.equals("org.junit.jupiter.api.Disabled")
                    || resolved.equals("org.junit.Ignore")
                    || resolved.equals("Disabled")
                    || resolved.equals("Ignore");
        }

        private boolean isDisplayName(Ann ann) {
            String resolved = imports.resolve(ann.name);
            return resolved.equals("org.junit.jupiter.api.DisplayName") || resolved.equals("DisplayName");
        }

        private Framework frameworkOf(List<Ann> annotations) {
            for (Ann ann : annotations) {
                if (!isJUnitTest(List.of(ann))) {
                    continue;
                }
                String resolved = imports.resolve(ann.name);
                if (resolved.startsWith("org.junit.jupiter")
                        || resolved.equals("ParameterizedTest")
                        || resolved.equals("RepeatedTest")
                        || resolved.equals("TestFactory")
                        || resolved.equals("TestTemplate")) {
                    return Framework.JUNIT5;
                }
                if (resolved.equals("org.junit.Test")
                        || resolved.equals("org.junit.experimental.theories.Theory")
                        || resolved.equals("Theory")) {
                    return Framework.JUNIT4;
                }
            }
            return Framework.UNKNOWN;
        }

        private String textOf(List<Ann> annotations, java.util.function.Predicate<Ann> predicate) {
            for (Ann ann : annotations) {
                if (predicate.test(ann)) {
                    return firstString(ann.args);
                }
            }
            return null;
        }

        private Ann readAnnotation() {
            int line = lineOf(i);
            i++;
            skipWsAndComments();
            if (eof() || !Character.isJavaIdentifierStart(ch())) {
                return new Ann("", null, line);
            }
            String name = readDotted();
            String args = null;
            skipWsAndComments();
            if (!eof() && ch() == '(') {
                int start = i + 1;
                skipBalancedParen();
                args = s.substring(start, Math.max(start, i - 1));
            }
            return new Ann(name, args, line);
        }

        private boolean consumeModifier() {
            if (lookingAt("non-sealed")) {
                i += "non-sealed".length();
                return true;
            }
            for (String modifier : MODIFIERS) {
                if (lookingAt(modifier)) {
                    i += modifier.length();
                    return true;
                }
            }
            return false;
        }

        private String matchKeyword(String... keywords) {
            for (String keyword : keywords) {
                if (lookingAt(keyword)) {
                    i += keyword.length();
                    return keyword;
                }
            }
            return null;
        }

        private String readDotted() {
            StringBuilder name = new StringBuilder(readIdentifier());
            while (!eof()) {
                int save = i;
                skipWsAndComments();
                if (eof() || ch() != '.') {
                    i = save;
                    break;
                }
                i++;
                int afterDot = i;
                skipWsAndComments();
                if (eof() || !Character.isJavaIdentifierStart(ch())) {
                    i = save;
                    break;
                }
                name.append('.').append(readIdentifier());
                if (i == afterDot) {
                    break;
                }
            }
            return name.toString();
        }

        private String readIdentifier() {
            if (eof() || !Character.isJavaIdentifierStart(ch())) {
                return "";
            }
            int start = i++;
            while (!eof() && Character.isJavaIdentifierPart(ch())) {
                i++;
            }
            return s.substring(start, i);
        }

        private void skipUntilSemicolon() {
            while (!eof() && ch() != ';') {
                if (ch() == '"' || ch() == '\'') {
                    skipLiteral();
                    continue;
                }
                if (isCommentStart()) {
                    skipComment();
                    continue;
                }
                i++;
            }
            if (!eof() && ch() == ';') {
                i++;
            }
        }

        private void skipBalancedParen() {
            int depth = 0;
            while (!eof()) {
                char c = ch();
                if (c == '"' || c == '\'') {
                    skipLiteral();
                    continue;
                }
                if (isCommentStart()) {
                    skipComment();
                    continue;
                }
                if (c == '(') {
                    depth++;
                    i++;
                    continue;
                }
                if (c == ')') {
                    depth--;
                    i++;
                    if (depth == 0) {
                        return;
                    }
                    continue;
                }
                i++;
            }
        }

        private void skipBlock() {
            int depth = 0;
            while (!eof()) {
                char c = ch();
                if (c == '"' || c == '\'') {
                    skipLiteral();
                    continue;
                }
                if (isCommentStart()) {
                    skipComment();
                    continue;
                }
                if (c == '{') {
                    depth++;
                    i++;
                    continue;
                }
                if (c == '}') {
                    depth--;
                    i++;
                    if (depth == 0) {
                        return;
                    }
                    continue;
                }
                i++;
            }
        }

        private void skipLiteral() {
            if (s.startsWith("\"\"\"", i)) {
                i += 3;
                while (i + 2 < n && !s.startsWith("\"\"\"", i)) {
                    if (s.charAt(i) == '\\' && i + 1 < n) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
                i = Math.min(n, i + 3);
                return;
            }
            char quote = s.charAt(i);
            i++;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\') {
                    i += i + 1 < n ? 2 : 1;
                    continue;
                }
                if (c == '\n' || c == '\r') {
                    i++;
                    return;
                }
                i++;
                if (c == quote) {
                    return;
                }
            }
        }

        private int skipLiteralAt(int index) {
            int save = i;
            i = index;
            skipLiteral();
            int next = i;
            i = save;
            return next;
        }

        private void skipComment() {
            if (i + 1 >= n) {
                i = n;
                return;
            }
            if (s.charAt(i + 1) == '/') {
                i += 2;
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
                return;
            }
            i += 2;
            while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                i++;
            }
            i = Math.min(n, i + 2);
        }

        private int skipCommentAt(int index) {
            int save = i;
            i = index;
            skipComment();
            int next = i;
            i = save;
            return next;
        }

        private void skipWsAndComments() {
            while (!eof()) {
                if (Character.isWhitespace(ch())) {
                    i++;
                    continue;
                }
                if (isCommentStart()) {
                    skipComment();
                    continue;
                }
                break;
            }
        }

        private boolean lookingAt(String keyword) {
            if (!s.startsWith(keyword, i)) {
                return false;
            }
            int end = i + keyword.length();
            return end >= n || !Character.isJavaIdentifierPart(s.charAt(end));
        }

        private boolean isCommentStart() {
            return ch() == '/' && i + 1 < n && (s.charAt(i + 1) == '/' || s.charAt(i + 1) == '*');
        }

        private int lineOf(int index) {
            int line = 1;
            int stop = Math.min(index, n);
            for (int k = 0; k < stop; k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                }
            }
            return line;
        }

        private char ch() {
            return s.charAt(i);
        }

        private boolean eof() {
            return i >= n;
        }
    }

    private static String firstString(String args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length(); i++) {
            if (args.charAt(i) == '"') {
                return readJavaString(args, i);
            }
        }
        return null;
    }

    private static String readJavaString(String value, int quote) {
        StringBuilder out = new StringBuilder();
        for (int i = quote + 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(next);
                }
                continue;
            }
            if (c == '"') {
                return out.toString();
            }
            out.append(c);
        }
        return out.toString();
    }

    private record Ann(String name, String args, int line) {
    }

    private record Ident(String name, int index) {
    }

    private record MethodHit(String name, int index) {
    }

    private enum Term {
        SEMI, BRACE, CLOSE, EOF
    }

    private static final class Imports {
        private final Map<String, String> types = new HashMap<>();
        private boolean starJupiter;
        private boolean starJunit;

        private String resolve(String raw) {
            if (raw == null || raw.isBlank()) {
                return "";
            }
            if (raw.indexOf('.') >= 0) {
                return raw;
            }
            String mapped = types.get(raw);
            if (mapped != null) {
                return mapped;
            }
            if (starJupiter && JUPITER_SIMPLE.contains(raw)) {
                return "org.junit.jupiter.api." + raw;
            }
            if (starJunit && raw.equals("Test")) {
                return "org.junit.Test";
            }
            if (starJunit && raw.equals("Ignore")) {
                return "org.junit.Ignore";
            }
            return raw;
        }
    }
}
