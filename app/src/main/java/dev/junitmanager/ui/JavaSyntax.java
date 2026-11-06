package dev.junitmanager.ui;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.util.Set;

final class JavaSyntax {
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "non-sealed", "package", "permits", "private", "protected", "public", "record", "return",
            "sealed", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "try", "var", "void", "volatile", "while", "yield", "true", "false", "null");

    private static final Color DARK_TEXT = new Color(0xBC, 0xBE, 0xC4);
    private static final Color DARK_KEYWORD = new Color(0xCF, 0x8E, 0x6D);
    private static final Color DARK_STRING = new Color(0x6A, 0xAB, 0x73);
    private static final Color DARK_NUMBER = new Color(0x2A, 0xAC, 0xB8);
    private static final Color DARK_COMMENT = new Color(0x7A, 0x7E, 0x85);
    private static final Color DARK_ANNOTATION = new Color(0xBB, 0xB5, 0x29);

    private static final Color LIGHT_TEXT = new Color(0x08, 0x08, 0x08);
    private static final Color LIGHT_KEYWORD = new Color(0x00, 0x33, 0xB3);
    private static final Color LIGHT_STRING = new Color(0x06, 0x7D, 0x17);
    private static final Color LIGHT_NUMBER = new Color(0x17, 0x50, 0xEB);
    private static final Color LIGHT_COMMENT = new Color(0x8C, 0x8C, 0x8C);
    private static final Color LIGHT_ANNOTATION = new Color(0x9E, 0x88, 0x0D);

    private JavaSyntax() {
    }

    static void apply(StyledDocument document, boolean dark, Font font) {
        int length = document.getLength();
        if (length == 0) {
            return;
        }
        String text;
        try {
            text = document.getText(0, length);
        } catch (BadLocationException ex) {
            return;
        }
        SimpleAttributeSet plain = style(dark ? DARK_TEXT : LIGHT_TEXT, font);
        SimpleAttributeSet keyword = style(dark ? DARK_KEYWORD : LIGHT_KEYWORD, font);
        SimpleAttributeSet string = style(dark ? DARK_STRING : LIGHT_STRING, font);
        SimpleAttributeSet number = style(dark ? DARK_NUMBER : LIGHT_NUMBER, font);
        SimpleAttributeSet comment = style(dark ? DARK_COMMENT : LIGHT_COMMENT, font);
        SimpleAttributeSet annotation = style(dark ? DARK_ANNOTATION : LIGHT_ANNOTATION, font);
        document.setCharacterAttributes(0, length, plain, true);
        int index = 0;
        while (index < length) {
            char current = text.charAt(index);
            if (current == '/' && index + 1 < length) {
                char next = text.charAt(index + 1);
                if (next == '/') {
                    int end = text.indexOf('\n', index);
                    paint(document, index, end < 0 ? length : end, comment);
                    index = end < 0 ? length : end;
                    continue;
                }
                if (next == '*') {
                    int close = text.indexOf("*/", index + 2);
                    int end = close < 0 ? length : close + 2;
                    paint(document, index, end, comment);
                    index = end;
                    continue;
                }
            }
            if (current == '"') {
                int end = text.startsWith("\"\"\"", index)
                        ? close(text, index + 3, "\"\"\"", length)
                        : skipQuoted(text, index, length);
                paint(document, index, end, string);
                index = end;
                continue;
            }
            if (current == '\'') {
                int end = skipQuoted(text, index, length);
                paint(document, index, end, string);
                index = end;
                continue;
            }
            if (current == '@') {
                int end = index + 1;
                while (end < length && Character.isJavaIdentifierPart(text.charAt(end))) {
                    end++;
                }
                paint(document, index, end, annotation);
                index = end;
                continue;
            }
            if (Character.isJavaIdentifierStart(current)) {
                int end = index + 1;
                while (end < length && Character.isJavaIdentifierPart(text.charAt(end))) {
                    end++;
                }
                if (KEYWORDS.contains(text.substring(index, end))) {
                    paint(document, index, end, keyword);
                }
                index = end;
                continue;
            }
            if (Character.isDigit(current)) {
                int end = index + 1;
                while (end < length && isNumberChar(text.charAt(end))) {
                    end++;
                }
                paint(document, index, end, number);
                index = end;
                continue;
            }
            index++;
        }
    }

    private static int close(String text, int from, String marker, int length) {
        int found = text.indexOf(marker, from);
        return found < 0 ? length : found + marker.length();
    }

    private static int skipQuoted(String text, int start, int length) {
        int index = start + 1;
        while (index < length) {
            char current = text.charAt(index);
            if (current == '\\') {
                index = Math.min(length, index + 2);
                continue;
            }
            if (current == text.charAt(start) || current == '\n') {
                return current == '\n' ? index : index + 1;
            }
            index++;
        }
        return length;
    }

    private static boolean isNumberChar(char current) {
        return Character.isDigit(current)
                || current == '_'
                || current == '.'
                || (current >= 'a' && current <= 'f')
                || (current >= 'A' && current <= 'F')
                || current == 'x'
                || current == 'X'
                || current == 'l'
                || current == 'L'
                || current == 'd'
                || current == 'D'
                || current == 'f'
                || current == 'F';
    }

    private static void paint(StyledDocument document, int start, int end, SimpleAttributeSet attributes) {
        if (end > start) {
            document.setCharacterAttributes(start, end - start, attributes, true);
        }
    }

    private static SimpleAttributeSet style(Color color, Font font) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        StyleConstants.setFontFamily(attributes, font.getFamily());
        StyleConstants.setFontSize(attributes, font.getSize());
        return attributes;
    }
}
