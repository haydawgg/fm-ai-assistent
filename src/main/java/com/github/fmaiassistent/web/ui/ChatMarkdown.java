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
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern INLINE_BOLD_ITALIC_STAR = Pattern.compile("(?<!\\\\)\\*\\*\\*(.+?)(?<!\\\\)\\*\\*\\*");
    private static final Pattern INLINE_BOLD_ITALIC_UNDERSCORE = Pattern.compile("(?<!\\\\)___(.+?)(?<!\\\\)___");
    private static final Pattern INLINE_BOLD_STAR = Pattern.compile("(?<!\\\\)\\*\\*(.+?)(?<!\\\\)\\*\\*");
    private static final Pattern INLINE_BOLD_UNDERSCORE = Pattern.compile("(?<![\\w\\\\])__(.+?)(?<![\\w\\\\])__(?!\\w)");
    private static final Pattern INLINE_ITALIC_STAR = Pattern.compile("(?<![\\*\\\\])\\*([^*]+?)(?<![\\*\\\\])\\*(?!\\*)");
    private static final Pattern INLINE_ITALIC_UNDERSCORE = Pattern.compile("(?<![\\w\\\\])_([^_]+?)(?<![\\w\\\\])_(?!\\w)");
    private static final Pattern INLINE_STRIKE = Pattern.compile("(?<!\\\\)~~(.+?)(?<!\\\\)~~");
    private static final Pattern INLINE_LINK = Pattern.compile("(?<!!)\\[([^\\]]+)\\]\\(([^)]+)\\)");

    static String sanitize(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        return stripDangerousHtml(renderTables(sanitizeLinks(markdown)));
    }

    private static String stripDangerousHtml(String html) {
        String result = html;
        result = result.replaceAll("(?is)<\\s*script\\b.*?</\\s*script\\s*>", "");
        result = result.replaceAll("(?is)<\\s*iframe\\b.*?</\\s*iframe\\s*>", "");
        result = result.replaceAll("(?is)<\\s*object\\b.*?</\\s*object\\s*>", "");
        result = result.replaceAll("(?is)<\\s*embed\\b.*?>", "");
        result = result.replaceAll("(?i)\\son\\w+\\s*=\\s*\"[^\"]*\"", "");
        result = result.replaceAll("(?i)\\son\\w+\\s*=\\s*'[^']*'", "");
        result = result.replaceAll("(?i)\\son\\w+\\s*=\\s*[^\\s>]+", "");
        result = result.replaceAll("(?i)\\sstyle\\s*=\\s*\"[^\"]*\"", "");
        result = result.replaceAll("(?i)\\sstyle\\s*=\\s*'[^']*'", "");
        return result;
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
            String destination = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int linkStart = matcher.start();
            int openBracket = linkStart;
            while (openBracket > last && markdown.charAt(openBracket - 1) != '['
                    && !(markdown.charAt(openBracket - 1) == '!' && openBracket >= 2
                    && markdown.charAt(openBracket - 2) == '[')) {
                openBracket--;
            }
            int labelStart = openBracket - 1;
            String prefix = markdown.substring(last, labelStart);
            String label = markdown.substring(labelStart, linkStart + 1);
            boolean isImage = labelStart > last && markdown.charAt(labelStart - 1) == '!';
            if (dangerousScheme(decodeDestination(destination), isImage)) {
                out.append(prefix).append(label).append("(#blocked-)");
            } else {
                out.append(prefix).append(label).append(markdown, linkStart + 1, matcher.end());
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
            if (matcher.matches() && dangerousScheme(decodeDestination(matcher.group(2)), false)) {
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

    private static boolean dangerousScheme(String destination, boolean isImage) {
        String scheme = decodeDestination(destination).strip().toLowerCase(Locale.ROOT);
        if (scheme.startsWith("<")) {
            int end = scheme.indexOf('>');
            scheme = (end > 0 ? scheme.substring(1, end) : scheme.substring(1)).strip();
        }
        scheme = scheme.replaceAll("[\\s\\u00a0]+", "");
        if (scheme.contains("&")) {
            scheme = decodeRemainingEntities(scheme);
        }
        if (scheme.startsWith("javascript:") || scheme.startsWith("vbscript:")) {
            return true;
        }
        if (scheme.startsWith("data:")) {
            if (isImage) {
                return !(scheme.startsWith("data:image/png")
                        || scheme.startsWith("data:image/jpeg")
                        || scheme.startsWith("data:image/gif")
                        || scheme.startsWith("data:image/webp"));
            }
            return true;
        }
        // Chat output is rendered inside the desktop webview. Keep navigation
        // on web links (and harmless relative links) so file:, blob:, ftp: and
        // custom application schemes cannot reach the renderer.
        if (scheme.startsWith("http://") || scheme.startsWith("https://")
                || scheme.startsWith("mailto:") || scheme.startsWith("//")
                || !scheme.contains(":")) {
            return false;
        }
        return true;
    }

    private static String decodeRemainingEntities(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '&') {
                int semicolon = text.indexOf(';', index + 1);
                if (semicolon > index && semicolon - index <= 20) {
                    String entity = text.substring(index + 1, semicolon);
                    Character decoded = decodeEntity(entity);
                    if (decoded != null) {
                        out.append(decoded);
                        index = semicolon + 1;
                        continue;
                    }
                    // Unrecognized entity — the browser will still decode it.
                    // Strip the & and ; so it can't form a dangerous scheme.
                    out.append(entity).append(' ');
                    index = semicolon + 1;
                    continue;
                }
            }
            out.append(current);
            index++;
        }
        return out.toString();
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
            case "colon" -> ':';
            case "sol" -> '/';
            case "num" -> '#';
            case "period" -> '.';
            case "lpar" -> '(';
            case "rpar" -> ')';
            case "comma" -> ',';
            case "semi" -> ';';
            case "quest" -> '?';
            case "excl" -> '!';
            case "commat" -> '@';
            case "percnt" -> '%';
            case "equals" -> '=';
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
            if (isTableRow(lines[index]) && !isSeparator(lines[index])) {
                List<String> block = pipeTableBlock(lines, index);
                if (block.size() >= 3 && !containsSeparator(block)) {
                    out.append(toHtmlTable(block));
                    index += block.size();
                    if (index < lines.length) {
                        out.append('\n');
                    }
                    continue;
                }
            }
            if (isTabTableRow(lines[index]) && index + 1 < lines.length && isTabTableRow(lines[index + 1])) {
                List<String> block = new ArrayList<>();
                block.add(lines[index]);
                index++;
                while (index < lines.length && isTabTableRow(lines[index])) {
                    block.add(lines[index]);
                    index++;
                }
                out.append(toHtmlTabTable(block));
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

    private static List<String> pipeTableBlock(String[] lines, int start) {
        List<String> block = new ArrayList<>();
        int index = start;
        int columns = cellCount(lines[index]);
        while (index < lines.length) {
            String line = lines[index];
            if (!isTableRow(line)) {
                break;
            }
            if (cellCount(line) != columns) {
                break;
            }
            block.add(line);
            index++;
        }
        return block;
    }

    private static boolean containsSeparator(List<String> block) {
        for (String line : block) {
            if (isSeparator(line)) {
                return true;
            }
        }
        return false;
    }

    private static int cellCount(String line) {
        return cells(line).size();
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
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("|") && trimmed.contains("|")) {
            return true;
        }
        return trimmed.contains("|") && !trimmed.startsWith("#") && !trimmed.startsWith("```");
    }

    private static boolean isSeparator(String line) {
        String trimmed = line == null ? "" : line.strip();
        if (trimmed.isEmpty() || !trimmed.contains("-")) {
            return false;
        }
        String inner = trimmed.replace("|", "").replace(":", "").replace("-", "").strip();
        return inner.isEmpty();
    }

    private static boolean isTabTableRow(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.strip();
        return trimmed.contains("\t") && trimmed.split("\t", -1).length >= 2;
    }

    private static List<String> parseAlignments(String separatorRow) {
        List<String> aligns = new ArrayList<>();
        for (String cell : cells(separatorRow)) {
            String trimmed = cell.strip();
            boolean left = trimmed.startsWith(":");
            boolean right = trimmed.endsWith(":");
            if (left && right) {
                aligns.add("center");
            } else if (right) {
                aligns.add("right");
            } else if (left) {
                aligns.add("left");
            } else {
                aligns.add("");
            }
        }
        return aligns;
    }

    private static String alignAttr(String align) {
        if (align == null || align.isEmpty()) {
            return "";
        }
        return " align=\"" + align + "\"";
    }

    private static String toHtmlTable(List<String> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        List<String> alignments = rows.size() > 1 && isSeparator(rows.get(1)) ? parseAlignments(rows.get(1)) : List.of();
        StringBuilder html = new StringBuilder("<table>\n<thead>\n<tr>");
        List<String> headerCells = cells(rows.getFirst());
        for (int i = 0; i < headerCells.size(); i++) {
            String align = i < alignments.size() ? alignments.get(i) : "";
            html.append("<th").append(alignAttr(align)).append(">").append(renderInline(headerCells.get(i))).append("</th>");
        }
        html.append("</tr>\n</thead>\n<tbody>\n");
        int startIndex = rows.size() > 1 && isSeparator(rows.get(1)) ? 2 : 1;
        for (int index = startIndex; index < rows.size(); index++) {
            html.append("<tr>");
            List<String> rowCells = cells(rows.get(index));
            for (int i = 0; i < rowCells.size(); i++) {
                String align = i < alignments.size() ? alignments.get(i) : "";
                html.append("<td").append(alignAttr(align)).append(">").append(renderInline(rowCells.get(i))).append("</td>");
            }
            html.append("</tr>\n");
        }
        return html.append("</tbody>\n</table>").toString();
    }

    private static String toHtmlTabTable(List<String> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<table>\n<thead>\n<tr>");
        String[] headerCells = rows.getFirst().split("\t", -1);
        for (String cell : headerCells) {
            html.append("<th>").append(renderInline(cell.strip())).append("</th>");
        }
        html.append("</tr>\n</thead>\n<tbody>\n");
        for (int index = 1; index < rows.size(); index++) {
            html.append("<tr>");
            String[] rowCells = rows.get(index).split("\t", -1);
            for (String cell : rowCells) {
                html.append("<td>").append(renderInline(cell.strip())).append("</td>");
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

    static String renderInline(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        List<String> codeSpans = new ArrayList<>();
        Matcher codeMatcher = INLINE_CODE.matcher(value);
        StringBuilder withoutCode = new StringBuilder();
        int last = 0;
        while (codeMatcher.find()) {
            withoutCode.append(value, last, codeMatcher.start());
            withoutCode.append("\u0000CODE").append(codeSpans.size()).append("\u0000");
            codeSpans.add(codeMatcher.group(1));
            last = codeMatcher.end();
        }
        withoutCode.append(value, last, value.length());

        String text = escape(withoutCode.toString());

        text = INLINE_BOLD_ITALIC_STAR.matcher(text).replaceAll("<strong><em>$1</em></strong>");
        text = INLINE_BOLD_ITALIC_UNDERSCORE.matcher(text).replaceAll("<strong><em>$1</em></strong>");
        text = INLINE_BOLD_STAR.matcher(text).replaceAll("<strong>$1</strong>");
        text = INLINE_BOLD_UNDERSCORE.matcher(text).replaceAll("<strong>$1</strong>");
        text = INLINE_ITALIC_STAR.matcher(text).replaceAll("<em>$1</em>");
        text = INLINE_ITALIC_UNDERSCORE.matcher(text).replaceAll("<em>$1</em>");
        text = INLINE_STRIKE.matcher(text).replaceAll("<del>$1</del>");

        Matcher linkMatcher = INLINE_LINK.matcher(text);
        StringBuilder linkOut = new StringBuilder();
        last = 0;
        while (linkMatcher.find()) {
            linkOut.append(text, last, linkMatcher.start());
            String label = linkMatcher.group(1);
            String dest = linkMatcher.group(2).replace("\"", "&quot;");
            if (dangerousScheme(linkMatcher.group(2), false)) {
                linkOut.append("<a href=\"#blocked-\">").append(label).append("</a>");
            } else {
                linkOut.append("<a href=\"").append(dest).append("\">").append(label).append("</a>");
            }
            last = linkMatcher.end();
        }
        linkOut.append(text, last, text.length());
        text = linkOut.toString();

        for (int i = 0; i < codeSpans.size(); i++) {
            text = text.replace("\u0000CODE" + i + "\u0000", "<code>" + escape(codeSpans.get(i)) + "</code>");
        }

        return text;
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;");
    }
}
