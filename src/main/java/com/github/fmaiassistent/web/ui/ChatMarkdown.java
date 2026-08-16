package com.github.fmaiassistent.web.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChatMarkdown {
    private ChatMarkdown() {
    }

    private static final Pattern LINK_DESTINATION = Pattern.compile("\\]\\((?:<([^>]*)>|([^)]*))\\)");
    private static final Pattern REFERENCE_DEFINITION = Pattern.compile("^(\\s*\\[[^\\]]+\\]:\\s*)(\\S+.*)$");

    static String sanitize(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        return renderTables(sanitizeLinks(markdown));
    }

    static String sanitizeLinks(String markdown) {
        String withInline = rewriteInlineLinks(markdown);
        return rewriteReferenceDefinitions(withInline);
    }

    private static String rewriteInlineLinks(String markdown) {
        Matcher matcher = LINK_DESTINATION.matcher(markdown);
        StringBuilder out = new StringBuilder(markdown.length());
        int last = 0;
        while (matcher.find()) {
            out.append(markdown, last, matcher.start());
            String destination = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (dangerousScheme(decodeDestination(destination))) {
                out.append("](#blocked-)");
            } else {
                out.append(matcher.group());
            }
            last = matcher.end();
        }
        if (last < markdown.length()) {
            out.append(markdown, last, markdown.length());
        }
        return out.toString();
    }

    private static String rewriteReferenceDefinitions(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder out = new StringBuilder(markdown.length());
        for (int index = 0; index < lines.length; index++) {
            Matcher matcher = REFERENCE_DEFINITION.matcher(lines[index]);
            if (matcher.matches() && dangerousScheme(decodeDestination(matcher.group(2)))) {
                out.append(matcher.group(1)).append("[#blocked-]");
            } else {
                out.append(lines[index]);
            }
            if (index + 1 < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static boolean dangerousScheme(String destination) {
        String scheme = destination.strip().toLowerCase(Locale.ROOT);
        if (scheme.startsWith("<")) {
            int end = scheme.indexOf('>');
            scheme = (end > 0 ? scheme.substring(1, end) : scheme.substring(1)).strip();
        }
        scheme = scheme.replaceAll("[\\s\\u00a0]+", "");
        return scheme.startsWith("javascript:") || scheme.startsWith("data:") || scheme.startsWith("vbscript:");
    }

    static String decodeDestination(String destination) {
        String text = destination == null ? "" : destination;
        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '&') {
                int semicolon = text.indexOf(';', index + 1);
                if (semicolon > index && semicolon - index <= 10) {
                    String entity = text.substring(index + 1, semicolon);
                    Character decoded = decodeEntity(entity);
                    if (decoded != null) {
                        out.append(decoded);
                        index = semicolon + 1;
                        continue;
                    }
                }
                out.append(current);
                index++;
            } else if (current == '%' && index + 2 < text.length()) {
                String hex = text.substring(index + 1, index + 3);
                int value;
                try {
                    value = Integer.parseInt(hex, 16);
                } catch (NumberFormatException e) {
                    out.append(current);
                    index++;
                    continue;
                }
                out.append((char) value);
                index += 3;
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }

    private static Character decodeEntity(String entity) {
        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            try {
                return (char) Integer.parseInt(entity.substring(2), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (entity.startsWith("#")) {
            try {
                return (char) Integer.parseInt(entity.substring(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (entity) {
            case "amp" -> '&';
            case "lt" -> '<';
            case "gt" -> '>';
            case "quot" -> '"';
            case "apos", "#39" -> '\'';
            case "nbsp" -> ' ';
            case "Tab" -> '\t';
            case "NewLine" -> '\n';
            default -> null;
        };
    }

    static String renderTables(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < lines.length) {
            String fence = fenceLanguage(lines[index]);
            if (fence != null) {
                StringBuilder code = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < lines.length) {
                    if (fenceLanguage(lines[index]) != null) {
                        closed = true;
                        index++;
                        break;
                    }
                    if (code.length() > 0) {
                        code.append('\n');
                    }
                    code.append(codeEscape(lines[index]));
                    index++;
                }
                out.append("<pre><code");
                if (!fence.isBlank()) {
                    out.append(" class=\"language-").append(fence).append('"');
                }
                out.append('>').append(code);
                if (closed && code.length() == 0) {
                    out.append('\n');
                }
                out.append("</code></pre>");
                if (index < lines.length) {
                    out.append('\n');
                }
                continue;
            }
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

    private static String fenceLanguage(String line) {
        if (line == null || !line.startsWith("```")) {
            return null;
        }
        String rest = line.substring(3).strip();
        if (!rest.isEmpty() && !rest.matches("[a-zA-Z0-9_+\\-]+")) {
            return null;
        }
        return rest;
    }

    private static String codeEscape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
