package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMarkdownTest {
    @Test
    void pipeTablesBecomeHtml() {
        String html = ChatMarkdown.sanitize("""
                | Name | CA |
                | --- | --- |
                | Ada <x> | 150 |
                """);
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>Name</th>"));
        assertTrue(html.contains("<td>Ada &lt;x&gt;</td>"));
        assertTrue(html.contains("<td>150</td>"));
    }

    @Test
    void nonTablesStayEscaped() {
        assertEquals("a &amp; b", ChatMarkdown.sanitize("a & b"));
    }
}
