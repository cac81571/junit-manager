package dev.junitmanager.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnsiTextTest {
    @Test
    void keepsInfoTagAndPaintsBuildFailureSeparately() {
        String line = "[\u001B[1;34mINFO\u001B[m] \u001B[1;31mBUILD FAILURE\u001B[m\n";
        List<AnsiText.Span> spans = AnsiText.parse(line, new AnsiText.State());

        StringBuilder visible = new StringBuilder();
        spans.forEach(span -> visible.append(span.text()));
        assertEquals("[INFO] BUILD FAILURE\n", visible.toString());

        AnsiText.Span info = spans.stream().filter(span -> span.text().equals("INFO")).findFirst().orElseThrow();
        AnsiText.Span failure = spans.stream().filter(span -> span.text().equals("BUILD FAILURE")).findFirst().orElseThrow();
        assertEquals(4, info.fg());
        assertTrue(info.bold());
        assertEquals(1, failure.fg());
        assertTrue(failure.bold());
        assertEquals(-1, spans.get(0).fg());
    }
}
