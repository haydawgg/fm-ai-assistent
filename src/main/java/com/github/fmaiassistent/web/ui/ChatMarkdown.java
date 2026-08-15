package com.github.fmaiassistent.web.ui;

import java.util.ArrayList;
import java.util.List;

final class ChatMarkdown {
    private ChatMarkdown() {
    }

    static String sanitize(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        return renderTables(markdown)
                .replaceAll("(?i)]\\s*\\((?:javascript|data|vbscript):", "](#blocked-");
    }

    static String renderTables(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < lines.length) {
            if (isTableRow(lines[index]) && index + 1 < lines.length && isSeparator(lines[index + 1])) {
                List<String> block = new ArrayList<>();
                block.add(lines[index]);
                block.add(lines[index + 1]);
                index += 2;
                while (index < lines.length && isTableRow(lines[index])) {
                    block.add(lines[index]);
                    index++;
                }
                out.append(toHtmlTable(block));
                if (index < lines.length) {
                    out.append('\n');
                }
                continue;
            }
            out.append(escape(lines[index]));
            if (index + 1 < lines.length) {
                out.append('\n');
            }
            index++;
        }
        return out.toString();
    }

    private static boolean isTableRow(String line) {
        String trimmed = line == null ? "" : line.strip();
        return trimmed.startsWith("|") && trimmed.contains("|");
    }

    private static boolean isSeparator(String line) {
        String trimmed = line == null ? "" : line.strip();
        if (!trimmed.startsWith("|")) {
            return false;
        }
        String inner = trimmed.replace("|", "").replace(":", "").replace("-", "").strip();
        return inner.isEmpty() && trimmed.contains("-");
    }

    private static String toHtmlTable(List<String> rows) {
        StringBuilder html = new StringBuilder("<table>\n<thead>\n<tr>");
        for (String cell : cells(rows.getFirst())) {
            html.append("<th>").append(escape(cell)).append("</th>");
        }
        html.append("</tr>\n</thead>\n<tbody>\n");
        for (int index = 2; index < rows.size(); index++) {
            html.append("<tr>");
            for (String cell : cells(rows.get(index))) {
                html.append("<td>").append(escape(cell)).append("</td>");
            }
            html.append("</tr>\n");
        }
        return html.append("</tbody>\n</table>").toString();
    }

    private static List<String> cells(String row) {
        String trimmed = row.strip();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : trimmed.split("\\|", -1)) {
            cells.add(cell.strip());
        }
        return cells;
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
