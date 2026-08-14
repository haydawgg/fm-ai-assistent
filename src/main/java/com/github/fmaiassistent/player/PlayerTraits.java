package com.github.fmaiassistent.player;

import com.github.fmaiassistent.linux.FmMemoryStrings;
import com.github.fmaiassistent.memory.ProcessMemoryReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class PlayerTraits {
    private static final int SCAN_FROM = -0x1C0;
    private static final int SCAN_TO = 0x1C0;
    private static final int MAX_TRAITS = 16;

    private static final Set<String> CATALOG = Set.of(
            "places shots",
            "shoots with power",
            "shoots from distance",
            "tries long range free kicks",
            "tries first time shots",
            "refrains from taking long shots",
            "likes to round keeper",
            "likes ball played into feet",
            "plays one-twos",
            "tries killer balls often",
            "plays no through balls",
            "looks for pass rather than attempting to score",
            "dictates tempo",
            "knocks ball past opponent",
            "runs with ball often",
            "runs with ball rarely",
            "runs with ball down left",
            "runs with ball down right",
            "runs with ball through centre",
            "cuts inside from both wings",
            "hugs line",
            "stays back at all times",
            "gets forward whenever possible",
            "gets into opposition area",
            "comes deep to get ball",
            "likes to switch ball to other flank",
            "plays short simple passes",
            "tries long range passes",
            "uses long throw to start counter attacks",
            "uses outside of foot",
            "curls ball",
            "moves into channels",
            "plays with back to goal",
            "argues with officials",
            "dives into tackles",
            "does not dive into tackles",
            "marks opponent tightly",
            "tries to play way out of trouble",
            "brings ball out of defence",
            "tries tricks",
            "avoids using weaker foot",
            "dwells on ball",
            "stops play",
            "winds up opponents",
            "flair passes",
            "hits free kicks with power",
            "likes to try to beat offside trap",
            "plays one twos",
            "tries killer balls",
            "cut inside from left wing",
            "cut inside from right wing",
            "hugs the touchline",
            "gets forward often",
            "arrives late in opponents area",
            "comes deep",
            "switches play",
            "plays short passes",
            "long range passer",
            "uses long throws",
            "outside of foot",
            "dive into tackles",
            "marks tightly",
            "plays way out of trouble",
            "brings ball out of defense",
            "avoids weaker foot",
            "dwells on the ball");

    private PlayerTraits() {
    }

    public static String read(ProcessMemoryReader reader, long record) {
        for (int offset = SCAN_FROM; offset <= SCAN_TO; offset += 8) {
            List<String> names = readVector(reader, record + offset);
            if (names.isEmpty()) {
                names = reader.qwordOrNull(record + offset)
                        .map(pointer -> readVector(reader, pointer))
                        .orElse(List.of());
            }
            if (!names.isEmpty()) {
                return String.join("; ", names);
            }
        }
        return "";
    }

    static List<String> readVector(ProcessMemoryReader reader, long vectorAddress) {
        try {
            long start = reader.readU64(vectorAddress);
            long end = reader.readU64(vectorAddress + 8);
            if (start == 0 || end < start || (end - start) % 8 != 0) {
                return List.of();
            }
            long count = (end - start) / 8;
            if (count < 1 || count > MAX_TRAITS) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (long index = 0; index < count; index++) {
                Optional<Long> item = reader.qwordOrNull(start + index * 8);
                if (item.isEmpty()) {
                    return List.of();
                }
                String name = traitName(reader, item.get());
                if (name == null) {
                    return List.of();
                }
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
            return names.size() >= 1 ? List.copyOf(names) : List.of();
        } catch (IOException | RuntimeException ex) {
            return List.of();
        }
    }

    private static String traitName(ProcessMemoryReader reader, long item) {
        Optional<String> name = FmMemoryStrings.objectStringAt(reader, item, 0x20)
                .or(() -> FmMemoryStrings.objectStringAt(reader, item, 0x18))
                .or(() -> FmMemoryStrings.objectStringAt(reader, item, 0x28))
                .or(() -> FmMemoryStrings.probeString(reader, item));
        if (name.isEmpty()) {
            return "";
        }
        String text = name.get().trim();
        if (text.isBlank()) {
            return "";
        }
        return isKnown(text) ? text : null;
    }

    static boolean isKnown(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace('’', '\'');
        if (CATALOG.contains(key)) {
            return true;
        }
        String compact = key.replace("-", " ").replace("  ", " ");
        return CATALOG.contains(compact);
    }
}
