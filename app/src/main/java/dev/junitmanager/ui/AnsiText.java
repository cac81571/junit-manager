package dev.junitmanager.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

final class AnsiText {
    static final String MODE = "log.mode";
    static final String FG = "log.fg";
    static final String BOLD = "log.bold";

    private static final Color[] DARK = {
            new Color(0x3A, 0x3A, 0x3A), new Color(0xF1, 0x4C, 0x4C), new Color(0x6A, 0xAB, 0x73), new Color(0xD2, 0x99, 0x22),
            new Color(0x56, 0xA8, 0xF5), new Color(0xC7, 0x7D, 0xBB), new Color(0x2A, 0xAC, 0xB8), new Color(0xBC, 0xBE, 0xC4),
            new Color(0x6E, 0x6E, 0x6E), new Color(0xFF, 0x7B, 0x72), new Color(0x8F, 0xDB, 0x98), new Color(0xE3, 0xB3, 0x41),
            new Color(0x79, 0xC0, 0xFF), new Color(0xD2, 0xA8, 0xFF), new Color(0x56, 0xD4, 0xDD), new Color(0xFF, 0xFF, 0xFF)
    };
    private static final Color[] LIGHT = {
            new Color(0x24, 0x29, 0x2F), new Color(0xC7, 0x2E, 0x29), new Color(0x06, 0x7D, 0x17), new Color(0x9A, 0x67, 0x00),
            new Color(0x17, 0x50, 0xEB), new Color(0xA6, 0x26, 0xA4), new Color(0x0B, 0x7A, 0x8B), new Color(0x24, 0x29, 0x2F),
            new Color(0x57, 0x60, 0x6A), new Color(0xE5, 0x53, 0x4B), new Color(0x1A, 0x7F, 0x37), new Color(0x9A, 0x67, 0x00),
            new Color(0x09, 0x69, 0xDA), new Color(0x82, 0x50, 0xDF), new Color(0x0B, 0x7A, 0x8B), new Color(0x24, 0x29, 0x2F)
    };

    private AnsiText() {
    }

    static Color color(int index, boolean dark) {
        Color[] palette = dark ? DARK : LIGHT;
        if (index < 0 || index >= palette.length) {
            return palette[7];
        }
        return palette[index];
    }

    static List<Span> parse(String text, State state) {
        List<Span> spans = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        int fg = state.fg;
        boolean bold = state.bold;
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) != '\u001B') {
                run.append(text.charAt(index));
                index++;
                continue;
            }
            if (run.length() > 0) {
                spans.add(new Span(run.toString(), fg, bold));
                run.setLength(0);
            }
            index = consume(text, index, state);
            fg = state.fg;
            bold = state.bold;
        }
        if (run.length() > 0) {
            spans.add(new Span(run.toString(), fg, bold));
        }
        return spans;
    }

    private static int consume(String text, int index, State state) {
        if (index + 1 >= text.length() || text.charAt(index + 1) != '[') {
            return index + 1;
        }
        int cursor = index + 2;
        while (cursor < text.length()) {
            char current = text.charAt(cursor);
            if (current >= 0x40 && current <= 0x7E) {
                if (current == 'm') {
                    state.sgr(text.substring(index + 2, cursor));
                }
                return cursor + 1;
            }
            cursor++;
        }
        return text.length();
    }

    record Span(String text, int fg, boolean bold) {
    }

    static final class State {
        private int fg = -1;
        private boolean bold;

        void reset() {
            fg = -1;
            bold = false;
        }

        void sgr(String body) {
            if (body.isEmpty()) {
                reset();
                return;
            }
            String[] parts = body.split(";", -1);
            int index = 0;
            while (index < parts.length) {
                int code = number(parts[index]);
                index++;
                if (code == 0) {
                    reset();
                } else if (code == 1) {
                    bold = true;
                } else if (code == 22) {
                    bold = false;
                } else if (code == 39) {
                    fg = -1;
                } else if (code >= 30 && code <= 37) {
                    fg = code - 30;
                } else if (code >= 90 && code <= 97) {
                    fg = code - 90 + 8;
                } else if ((code == 38 || code == 48) && index < parts.length) {
                    int mode = number(parts[index]);
                    if (mode == 5 && index + 1 < parts.length) {
                        int palette = number(parts[index + 1]);
                        if (code == 38 && palette >= 0 && palette < 16) {
                            fg = palette;
                        }
                        index += 2;
                    } else if (mode == 2) {
                        index = Math.min(parts.length, index + 4);
                    }
                }
            }
        }

        private static int number(String token) {
            if (token == null || token.isEmpty()) {
                return 0;
            }
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
    }
}
