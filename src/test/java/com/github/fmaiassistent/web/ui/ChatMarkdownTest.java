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

    @Test
    void nonWebSchemesAreBlocked() {
        assertTrue(ChatMarkdown.sanitize("[file](file:///C:/secret.txt)").contains("[file](#blocked-)"));
        assertTrue(ChatMarkdown.sanitize("[ftp](ftp://example.test/file)").contains("[ftp](#blocked-)"));
        assertTrue(ChatMarkdown.sanitize("[blob](blob:https://example.test/id)").contains("[blob](#blocked-)"));
        assertTrue(ChatMarkdown.renderInline("[x](https://example.test/path)")
                .contains("<a href=\"https://example.test/path\">"));
    }

    @Test
    void namedColonEntityCannotRebuildJavascriptScheme() {
        assertTrue(ChatMarkdown.sanitize("[x](java&colon;script:alert(1))").contains("[x](#blocked-)"));
    }

    @Test
    void tableCellsRenderBoldItalicCodeAndLinks() {
        String html = ChatMarkdown.sanitize("""
                | Player | Age | CA | Fee | Notes |
                | --- | --- | --- | --- | --- |
                | **Mattia Liberali** | 17 | **133** | **£525k** | Willingness **low** — `convincing` needed |
                """);
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<td><strong>Mattia Liberali</strong></td>"));
        assertTrue(html.contains("<td><strong>133</strong></td>"));
        assertTrue(html.contains("<td><strong>£525k</strong></td>"));
        assertTrue(html.contains("<td>Willingness <strong>low</strong> — <code>convincing</code> needed</td>"));
    }

    @Test
    void tabSeparatedTableBecomesHtmlTableWithFormatting() {
        String input = "Player\tAge\tClub\tCA\tPA\tFee\n"
                + "**Mattia Liberali**\t17\tBlu-neri (Inter)\t**133**\t**177**\t**£525k**\n"
                + "**Isaac Babadi**\t19\tPSV\t**129**\t**166**\t**£2.225M**";
        String html = ChatMarkdown.sanitize(input);
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>Player</th>"));
        assertTrue(html.contains("<th>Fee</th>"));
        assertTrue(html.contains("<td><strong>Mattia Liberali</strong></td>"));
        assertTrue(html.contains("<td>Blu-neri (Inter)</td>"));
        assertTrue(html.contains("<td><strong>133</strong></td>"));
        assertTrue(html.contains("<td><strong>£525k</strong></td>"));
        assertTrue(html.contains("<td><strong>Isaac Babadi</strong></td>"));
    }

    @Test
    void tableColumnAlignmentsArePreserved() {
        String html = ChatMarkdown.sanitize("""
                | Left | Center | Right |
                | :--- | :---: | ---: |
                | a | b | c |
                """);
        assertTrue(html.contains("<th align=\"left\">Left</th>"));
        assertTrue(html.contains("<th align=\"center\">Center</th>"));
        assertTrue(html.contains("<th align=\"right\">Right</th>"));
        assertTrue(html.contains("<td align=\"left\">a</td>"));
        assertTrue(html.contains("<td align=\"center\">b</td>"));
        assertTrue(html.contains("<td align=\"right\">c</td>"));
    }

    @Test
    void wonderkidReplyRendersWithoutLiteralAsterisks() {
        String reply = """
                🔍 **External Wonderkids** (CA ≥ 129, Affordable)
                Given your budget of £425k, most are out of reach unless you sell. Here are the best options:

                | Player | Age | Club | CA | PA | Fee | Wage | Notes |
                | --- | --- | --- | --- | --- | --- | --- | --- |
                | **Mattia Liberali** | 19 | Blu-neri (Inter) | **133** | **177** | **£525k** | £8k | Willingness **low** — may need convincing |
                | **Isaac Babadi** | 21 | PSV | **129** | **166** | **£2.225M** | £14.1k | Transfer-listed, but over budget |
                | **Jayden Danns** | 22 | Liverpool | **140** | **165** | **£4.65M** | £18.2k | Loan-listed — could try a loan |

                **My Take**
                Liberali is the closest to your budget and has huge upside.
                """;
        String html = ChatMarkdown.sanitize(reply);
        assertTrue(html.contains("<td><strong>Mattia Liberali</strong></td>"));
        assertTrue(html.contains("<td><strong>133</strong></td>"));
        assertTrue(html.contains("<td><strong>£2.225M</strong></td>"));
        assertTrue(html.contains("<td>Blu-neri (Inter)</td>"));
        assertEquals(0, countOccurrences(html, "<td>**"), "table cells must not leak literal markdown asterisks");
        assertEquals(0, countOccurrences(html, "**</td>"), "table cells must not leak literal markdown asterisks");
        assertEquals(0, countOccurrences(html, "<th>**"), "table headers must not leak literal markdown asterisks");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    @Test
    void pipeTableWithoutSeparatorRowStillBecomesHtmlTable() {
        String html = ChatMarkdown.sanitize("""
                | Player | CA |
                | **Ada** | 150 |
                | Bob | 120 |
                """);
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>Player</th>"));
        assertTrue(html.contains("<td><strong>Ada</strong></td>"));
        assertTrue(html.contains("<td>Bob</td>"));
    }

    @Test
    void blockedLinkKeepsItsLabel() {
        assertTrue(ChatMarkdown.sanitize("[click](javascript:alert(1))").contains("[click](#blocked-)"));
        assertTrue(ChatMarkdown.sanitize("![pic](javascript:alert(1))").contains("![pic](#blocked-)"));
    }

    @Test
    void linkDestinationQuotesAreEscaped() {
        String html = ChatMarkdown.renderInline("[x](https://a.test/path\" onmouseover=\"alert(1))");
        assertTrue(html.contains("<a href=\"https://a.test/path&quot; onmouseover=&quot;alert(1\">"));
    }
}
