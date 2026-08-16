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
        assertTrue(html.contains("<td>Ada &lt;x></td>"));
        assertTrue(html.contains("<td>150</td>"));
    }

    @Test
    void nonTablesStayEscaped() {
        assertEquals("a &amp; b", ChatMarkdown.sanitize("a & b"));
    }

    @Test
    void fencedCodeBecomesPreCode() {
        String html = ChatMarkdown.sanitize("""
                before
                ```java
                String s = "x <y> & z";
                ```
                after
                """);
        assertTrue(html.contains("before"));
        assertTrue(html.contains("after"));
        assertTrue(html.contains("<pre><code class=\"language-java\">"));
        assertTrue(html.contains("String s = &quot;x &lt;y&gt; &amp; z&quot;"));
        assertTrue(html.contains("</code></pre>"));
    }

    @Test
    void unclosedFenceStillEscapesContent() {
        String html = ChatMarkdown.sanitize("""
                ```sql
                SELECT * FROM t WHERE a < 5
                """);
        assertTrue(html.contains("<pre><code class=\"language-sql\">"));
        assertTrue(html.contains("WHERE a &lt; 5"));
    }

    @Test
    void javascriptLinksAreBlocked() {
        assertTrue(ChatMarkdown.sanitize("[x](javascript:alert(1))").contains("](#blocked-)"));
        assertTrue(ChatMarkdown.sanitize("[x](<javascript:alert(1)>)").contains("](#blocked-)"));
        assertTrue(ChatMarkdown.sanitize("[x][1]\n\n[1]: javascript:alert(1)").contains("[#blocked-]"));
        assertTrue(ChatMarkdown.sanitize("[x](java&#10;script:alert(1))").contains("](#blocked-)"));
    }
}
