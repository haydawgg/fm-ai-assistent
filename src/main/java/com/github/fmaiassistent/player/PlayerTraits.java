package com.github.fmaiassistent.player;

import com.github.fmaiassistent.linux.FmMemoryStrings;
import com.github.fmaiassistent.memory.ProcessMemoryReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class PlayerTraits {
    static final int SCAN_FROM = -0x1C0;
    static final int SCAN_TO = 0x1C0;
    static final int WINDOW_SIZE = SCAN_TO - SCAN_FROM + 16;
    static final int PREFERRED_REL = 0x40;
    private static final int MAX_TRAITS = 16;
    private static final int[] SCAN_ORDER = scanOrder();

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
        try {
            byte[] window = reader.readBytes(record + SCAN_FROM, WINDOW_SIZE);
            return namesFromWindow(reader, window);
        } catch (IOException | RuntimeException ex) {
            return namesFromOffsetHops(reader, record);
        }
    }

    private static String namesFromWindow(ProcessMemoryReader reader, byte[] window) {
        ByteBuffer buf = ByteBuffer.wrap(window).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset : SCAN_ORDER) {
            int pos = offset - SCAN_FROM;
            if (pos < 0 || pos + 16 > window.length) {
                continue;
            }
            long start = buf.getLong(pos);
            long end = buf.getLong(pos + 8);
            List<String> names = namesFromSpan(reader, start, end, offset == PREFERRED_REL);
            if (!names.isEmpty()) {
                return String.join("; ", names);
            }
        }
        return "";
    }

    private static String namesFromOffsetHops(ProcessMemoryReader reader, long record) {
        for (int offset : SCAN_ORDER) {
            try {
                long start = reader.readU64(record + offset);
                long end = reader.readU64(record + offset + 8);
                List<String> names = namesFromSpan(reader, start, end, offset == PREFERRED_REL);
                if (!names.isEmpty()) {
                    return String.join("; ", names);
                }
            } catch (IOException | RuntimeException ex) {
                // try the next offset
            }
        }
        return "";
    }

    static List<String> readVector(ProcessMemoryReader reader, long vectorAddress) {
        try {
            long start = reader.readU64(vectorAddress);
            long end = reader.readU64(vectorAddress + 8);
            return namesFromVector(reader, start, end);
        } catch (IOException | RuntimeException ex) {
            return List.of();
        }
    }

    private static List<String> namesFromSpan(
            ProcessMemoryReader reader, long start, long end, boolean followPointer) {
        List<String> names = namesFromVector(reader, start, end);
        if (!names.isEmpty() || !followPointer || plausibleItemSpan(start, end)) {
            return names;
        }
        if (start <= 0 || start > ProcessMemoryReader.MAX_USER_ADDRESS) {
            return List.of();
        }
        return readVector(reader, start);
    }

    private static boolean plausibleItemSpan(long start, long end) {
        if (start == 0 || end < start || (end - start) % 8 != 0) {
            return false;
        }
        long count = (end - start) / 8;
        return count >= 1 && count <= MAX_TRAITS;
    }

    private static List<String> namesFromVector(ProcessMemoryReader reader, long start, long end) {
        if (!plausibleItemSpan(start, end)) {
            return List.of();
        }
        long count = (end - start) / 8;
        List<String> names = new ArrayList<>();
        for (long index = 0; index < count; index++) {
            Optional<Long> item = reader.qwordOrNull(start + index * 8);
            if (item.isEmpty()) {
                continue;
            }
            String name = traitName(reader, item.get());
            if (name == null) {
                continue;
            }
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names.size() >= 1 ? List.copyOf(names) : List.of();
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

    private static int[] scanOrder() {
        int count = ((SCAN_TO - SCAN_FROM) / 8) + 1;
        int[] order = new int[count];
        order[0] = PREFERRED_REL;
        int index = 1;
        for (int offset = SCAN_FROM; offset <= SCAN_TO; offset += 8) {
            if (offset != PREFERRED_REL) {
                order[index++] = offset;
            }
        }
        return order;
    }
}
