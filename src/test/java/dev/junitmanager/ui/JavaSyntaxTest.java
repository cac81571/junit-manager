package dev.junitmanager.ui;

import org.junit.jupiter.api.Test;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaSyntaxTest {
    @Test
    void colorsKeywordsStringsCommentsAndAnnotations() throws Exception {
        DefaultStyledDocument document = new DefaultStyledDocument();
        String source = """
                void add() {
                    // note
                    return "ok";
                }
                @Test
                """;
        document.insertString(0, source, null);
        JavaSyntax.apply(document, true, new Font(Font.MONOSPACED, Font.PLAIN, 13));

        assertEquals(new Color(0xCF, 0x8E, 0x6D), colorAt(document, source.indexOf("void")));
        int comment = source.indexOf("//");
        assertEquals(new Color(0x7A, 0x7E, 0x85), colorAt(document, comment));
        int string = source.indexOf("\"ok\"");
        assertEquals(new Color(0x6A, 0xAB, 0x73), colorAt(document, string));
        int annotation = source.indexOf("@Test");
        assertEquals(new Color(0xBB, 0xB5, 0x29), colorAt(document, annotation));
    }

    private static Color colorAt(DefaultStyledDocument document, int offset) {
        return StyleConstants.getForeground(document.getCharacterElement(offset).getAttributes());
    }
}
