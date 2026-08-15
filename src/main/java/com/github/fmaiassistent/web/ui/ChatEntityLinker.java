package com.github.fmaiassistent.web.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ChatEntityLinker {
    private ChatEntityLinker() {
    }

    static List<String> mentions(String text, List<String> players, List<String> clubs) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        addHits(hits, haystack, players);
        addHits(hits, haystack, clubs);
        hits.sort(Comparator.comparingInt(String::length).reversed());
        return List.copyOf(hits);
    }

    private static void addHits(List<String> hits, String haystack, List<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name == null || name.strip().length() < 4) {
                continue;
            }
            String needle = name.strip();
            if (haystack.contains(needle.toLowerCase(Locale.ROOT)) && !containsIgnoreCase(hits, needle)) {
                hits.add(needle);
            }
        }
    }

    private static boolean containsIgnoreCase(List<String> names, String candidate) {
        for (String name : names) {
            if (name.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }
}
