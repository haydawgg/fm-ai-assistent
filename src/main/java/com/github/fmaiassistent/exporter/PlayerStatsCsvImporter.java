package com.github.fmaiassistent.exporter;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small, dependency-free importer for FM-style player and match CSV exports. */
public final class PlayerStatsCsvImporter {
    private PlayerStatsCsvImporter() {
    }

    public static ImportBatch parse(Reader input, String fallbackSeason, String scope) throws IOException {
        List<List<String>> records = records(input, delimiter(input));
        if (records.isEmpty()) return new ImportBatch(fallbackSeason, scope, List.of(), 0, 0);
        List<String> headers = records.getFirst().stream().map(PlayerStatsCsvImporter::header).toList();
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) columns.putIfAbsent(headers.get(i), i);
        String season = value(records.size() > 1 ? records.get(1) : List.of(), columns, "season");
        if (season == null || season.isBlank()) season = fallbackSeason == null ? "" : fallbackSeason;
        List<Row> rows = new ArrayList<>();
        int skipped = 0;
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            String name = value(record, columns, "name", "player", "playername");
            if (name == null || name.isBlank()) {
                skipped++;
                continue;
            }
            Map<String, Double> extras = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> column : columns.entrySet()) {
                if (KNOWN.contains(column.getKey())) continue;
                Double number = decimal(value(record, Map.of(column.getKey(), column.getValue()), column.getKey()));
                if (number != null) extras.put(canonicalMetricName(column.getKey()), number);
            }
            addNamedStat(extras, record, columns, "stat", "value");
            List<String> issues = new ArrayList<>();
            String appearancesText = value(record, columns, "appearances", "apps", "app");
            String startsText = value(record, columns, "starts", "start");
            String minutesText = value(record, columns, "minutes", "min");
            String goalsText = value(record, columns, "goals", "gls", "gl");
            String assistsText = value(record, columns, "assists", "ast", "ass");
            String ratingText = value(record, columns, "averagerating", "rating", "fmrating", "matchrating");
            Integer appearances = whole(appearancesText);
            Integer starts = whole(startsText);
            Integer minutes = whole(minutesText);
            Integer goals = whole(goalsText);
            Integer assists = whole(assistsText);
            Double rating = decimal(ratingText);
            invalidNumeric(issues, "appearances", appearancesText, appearances);
            invalidNumeric(issues, "starts", startsText, starts);
            invalidNumeric(issues, "minutes", minutesText, minutes);
            invalidNumeric(issues, "goals", goalsText, goals);
            invalidNumeric(issues, "assists", assistsText, assists);
            invalidDecimal(issues, "average_rating", ratingText, rating);
            if (minutes != null && goals != null && goals > minutes) {
                issues.add("goals exceed minutes");
                goals = null;
            }
            if (minutes != null && assists != null && assists > minutes) {
                issues.add("assists exceed minutes");
                assists = null;
            }
            if (appearances != null && starts != null && starts > appearances) {
                issues.add("starts exceed appearances");
                starts = null;
            }
            if (rating != null && rating > 10) {
                issues.add("average_rating exceeds 10");
                rating = null;
            }
            if (value(record, columns, "matchdate", "date", "dateplayed").isBlank()) {
                addDerivedAggregateMetrics(extras, minutes, goals, assists, appearances, starts);
            }
            rows.add(new Row(
                    name.trim(), value(record, columns, "club", "team"),
                    value(record, columns, "matchdate", "date", "dateplayed"),
                    value(record, columns, "competition", "league"),
                    value(record, columns, "opponent", "against"),
                    appearances, starts, minutes, goals, assists, rating,
                    validExtras(extras), List.copyOf(issues)));
        }
        int invalid = (int) rows.stream().filter(row -> !row.valid()).count();
        return new ImportBatch(season.trim(), scope == null || scope.isBlank() ? "all_competitions" : scope,
                List.copyOf(rows), skipped, invalid);
    }

    private static void invalidNumeric(List<String> issues, String field, String text, Integer value) {
        if (text != null && !text.isBlank() && value == null) issues.add("invalid " + field);
    }

    private static void invalidDecimal(List<String> issues, String field, String text, Double value) {
        if (text != null && !text.isBlank() && value == null) issues.add("invalid " + field);
    }

    private static final java.util.Set<String> KNOWN = java.util.Set.of(
            "name", "player", "playername", "club", "team", "season", "matchdate", "date", "dateplayed",
            "competition", "league", "opponent", "against", "appearances", "apps", "app", "starts", "start",
            "minutes", "min", "goals", "gls", "gl", "assists", "ast", "ass", "averagerating", "rating",
            "fmrating", "matchrating", "stat", "value");

    private static Map<String, Double> validExtras(Map<String, Double> values) {
        Map<String, Double> valid = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null && Double.isFinite(value) && value >= 0) valid.put(key, value);
        });
        return Map.copyOf(valid);
    }

    private static void addNamedStat(Map<String, Double> extras, List<String> row, Map<String, Integer> columns,
                                     String nameKey, String valueKey) {
        String stat = value(row, columns, nameKey);
        Double value = decimal(value(row, columns, valueKey));
        if (stat != null && !stat.isBlank() && value != null && value >= 0) {
            extras.put(canonicalMetricName(header(stat)), value);
        }
    }

    private static void addDerivedAggregateMetrics(Map<String, Double> extras, Integer minutes, Integer goals,
                                                   Integer assists, Integer appearances, Integer starts) {
        if (minutes != null && minutes > 0) {
            if (goals != null) extras.put("goals_per_90", goals * 90.0 / minutes);
            if (assists != null) extras.put("assists_per_90", assists * 90.0 / minutes);
        }
        if (appearances != null && appearances > 0) {
            if (minutes != null) extras.put("minutes_per_appearance", minutes.doubleValue() / appearances);
            if (starts != null) extras.put("starts_percentage", starts * 100.0 / appearances);
        }
    }

    private static String canonicalMetricName(String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "expectedgoals", "xgoals" -> "xg";
            case "expectedassists", "xassists" -> "xa";
            case "keypasses", "keypass" -> "key_passes";
            case "chancescreated", "chances" -> "chances_created";
            case "shotsontarget", "ontargetshots" -> "shots_on_target";
            case "passescompleted", "completedpasses" -> "passes_completed";
            case "tackleswon", "wontackles" -> "tackles_won";
            case "goalsper90", "goalsper90minutes" -> "goals_per_90";
            case "assistsper90", "assistsper90minutes" -> "assists_per_90";
            case "distancecovered" -> "distance";
            default -> value;
        };
    }

    private static Integer whole(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            double number = Double.parseDouble(text.trim().replace(',', '.'));
            if (!Double.isFinite(number) || number < 0 || number > Integer.MAX_VALUE || number != Math.rint(number)) return null;
            return (int) number;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double decimal(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            double number = Double.parseDouble(text.trim().replace(',', '.'));
            return Double.isFinite(number) && number >= 0 ? number : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String value(List<String> row, Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(name);
            if (index != null && index < row.size()) return row.get(index).trim();
        }
        return "";
    }

    private static String header(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static char delimiter(Reader input) throws IOException {
        if (input.markSupported()) {
            input.mark(8192);
            StringBuilder firstLine = new StringBuilder();
            int current;
            while ((current = input.read()) != -1 && current != '\n' && current != '\r') {
                firstLine.append((char) current);
            }
            input.reset();
            return chooseDelimiter(firstLine.toString());
        }
        return ',';
    }

    private static char chooseDelimiter(String line) {
        if (line == null) return ',';
        long semicolon = line.chars().filter(ch -> ch == ';').count();
        long tab = line.chars().filter(ch -> ch == '\t').count();
        return tab > semicolon && tab > 0 ? '\t' : semicolon > 0 ? ';' : ',';
    }

    private static List<List<String>> records(Reader input, char delimiter) throws IOException {
        List<List<String>> out = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int current;
        while ((current = input.read()) != -1) {
            char ch = (char) current;
            if (ch == '"') {
                if (quoted && input.markSupported()) {
                    input.mark(1);
                    int next = input.read();
                    if (next == '"') {
                        cell.append('"');
                        continue;
                    }
                    input.reset();
                }
                quoted = !quoted;
            } else if (ch == delimiter && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && input.markSupported()) {
                    input.mark(1);
                    int next = input.read();
                    if (next != '\n') input.reset();
                }
                row.add(cell.toString());
                cell.setLength(0);
                if (row.stream().anyMatch(value -> !value.isBlank())) out.add(List.copyOf(row));
                row.clear();
            } else {
                cell.append(ch);
            }
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            out.add(List.copyOf(row));
        }
        return out;
    }

    public record ImportBatch(String season, String scope, List<Row> rows, int skippedRows, int invalidRows) {
    }

    public record Row(String name, String club, String matchDate, String competition, String opponent,
                      Integer appearances, Integer starts, Integer minutes, Integer goals, Integer assists,
                      Double averageRating, Map<String, Double> extras, List<String> issues) {
        public Row {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public boolean valid() {
            return issues.isEmpty();
        }

        public boolean hasMatchContext() {
            return matchDate != null && !matchDate.isBlank();
        }
    }
}
