package com.github.fmaiassistent.linux;

import com.github.fmaiassistent.memory.ProcessMemoryReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDateFinderTest {
    private static final long GAME_PLUGIN_BASE = 0x1000_0000L;
    private static final long BUILD_238BDD_CURRENT_DATE_RVA = 0x4df3c18L;

    @Test
    void readsCurrentDateFromLinuxProtonLayout() {
        FakeReader reader = new FakeReader(ProcessMemoryReader.Platform.LINUX);
        putCurrentDate(reader, 0x7E00 | 8, 2028);

        LocalDate date = new GameDateFinder()
                .find(reader, 0x238bdd, GAME_PLUGIN_BASE)
                .orElseThrow();

        assertEquals(LocalDate.of(2028, 1, 8), date);
    }

    @Test
    void readsCurrentDateFromSameLayoutOnWindows() {
        FakeReader reader = new FakeReader(ProcessMemoryReader.Platform.WINDOWS);
        putCurrentDate(reader, 0x1A00 | 159, 2026);

        LocalDate date = new GameDateFinder()
                .find(reader, 0x238bdd, GAME_PLUGIN_BASE)
                .orElseThrow();

        assertEquals(LocalDate.of(2026, 6, 8), date);
    }

    @Test
    void ageYearsUsesBirthdayBoundary() {
        assertEquals(16, GameDateFinder.ageYears(LocalDate.of(2010, 2, 1), LocalDate.of(2026, 8, 16)));
        assertEquals(15, GameDateFinder.ageYears(LocalDate.of(2010, 2, 1), LocalDate.of(2026, 1, 31)));
    }

    @Test
    void ageYearsNeverGoesNegativeOnCorruptFutureDob() {
        // A garbled RAM date-of-birth later than the reference date must not produce a negative age in chat.
        assertEquals(0, GameDateFinder.ageYears(LocalDate.of(2030, 1, 1), GameDateFinder.DEFAULT_GAME_DATE));
        assertEquals(0, GameDateFinder.effectiveAge("", "2030-01-01", ""));
    }

    @Test
    void effectiveAgeFallsBackToDefaultGameDateWhenAgeAsOfIsMissing() {
        // Mattia Liberali (born 2007-04-06) should be 17 at default FM start (2024-07-01), not system calendar age
        assertEquals(17, GameDateFinder.effectiveAge("", "2007-04-06", ""));
        assertEquals(17, GameDateFinder.effectiveAge(null, "2007-04-06", null));
    }

    @Test
    void storedAgeWinsEvenWhenAgeAsOfIsEmpty() {
        // Export writes a computed age even when the RAM game date is unknown; age_as_of stays empty so
        // expiry/tenure code is not fooled, but effectiveAge must still prefer the stored age.
        assertEquals(17, GameDateFinder.effectiveAge("17", "2007-04-06", ""));
        assertEquals(17, GameDateFinder.effectiveAge("17", "2007-04-06", null));
    }

    @Test
    void chatAgesNeverDriftWithTheSystemClock() {
        // The exact wonderkid target dates from chat: ages must match the FM season baseline (2024-07-01),
        // never the live machine calendar (which was the original bug and inflated everyone by ~2 years).
        assertGameAge("Mattia Liberali", "2007-04-06", 17);
        assertGameAge("Isaac Babadi", "2005-04-06", 19);
        assertGameAge("Leandro Hernández", "2005-06-13", 19);
        assertGameAge("Jayden Danns", "2006-01-16", 18);
        assertGameAge("Abdallah Manga", "2010-01-01", 14);
    }

    private static void assertGameAge(String label, String dateOfBirth, int expected) {
        int gameAge = GameDateFinder.effectiveAge("", dateOfBirth, "");
        int clockAge = GameDateFinder.ageYears(LocalDate.parse(dateOfBirth), LocalDate.now());
        assertEquals(expected, gameAge, label + " must match the FM baseline age");
        assertTrue(gameAge != clockAge,
                label + " must not be derived from the system clock (would report " + clockAge + " in "
                        + LocalDate.now().getYear() + ")");
        assertEquals("2024-07-01", GameDateFinder.DEFAULT_GAME_DATE.toString());
    }

    @Test
    void estimatedDateRvaMatchesTheKnownBuild() {
        assertEquals(BUILD_238BDD_CURRENT_DATE_RVA, FmOffsets.estimatedCurrentDateRva(0x238bdd));
        assertEquals(BUILD_238BDD_CURRENT_DATE_RVA, FmOffsets.currentDateRva(0x238bdd));
    }

    @Test
    void maskedDayStripsFlagBitsSoPackedDatesValidate() {
        int packed = 0x7E00 | 8;
        assertEquals(8, GameDateFinder.maskedDay(packed));
        assertTrue(GameDateFinder.validDayYear(GameDateFinder.maskedDay(packed), 2028));
        assertTrue(!GameDateFinder.validDayYear(packed, 2028));
    }

    @Test
    void doesNotFallBackToAnotherBuild() {
        FakeReader reader = new FakeReader(ProcessMemoryReader.Platform.LINUX);
        putCurrentDate(reader, 8, 2028);

        assertTrue(new GameDateFinder()
                .find(reader, 0x235144, GAME_PLUGIN_BASE)
                .isEmpty());
    }

    private static void putCurrentDate(FakeReader reader, int day, int year) {
        reader.putU16(GAME_PLUGIN_BASE + BUILD_238BDD_CURRENT_DATE_RVA, day);
        reader.putU16(GAME_PLUGIN_BASE + BUILD_238BDD_CURRENT_DATE_RVA + Short.BYTES, year);
    }

    private static final class FakeReader implements ProcessMemoryReader {
        private final Map<Long, Byte> memory = new HashMap<>();
        private final Platform platform;

        private FakeReader(Platform platform) {
            this.platform = platform;
        }

        void putU16(long address, int value) {
            memory.put(address, (byte) value);
            memory.put(address + 1, (byte) (value >>> 8));
        }

        @Override
        public int pid() {
            return 1;
        }

        @Override
        public Platform platform() {
            return platform;
        }

        @Override
        public byte[] readBytes(long address, int size) throws IOException {
            byte[] bytes = new byte[size];
            for (int index = 0; index < size; index++) {
                Byte value = memory.get(address + index);
                if (value == null) {
                    throw new IOException("unmapped test address");
                }
                bytes[index] = value;
            }
            return bytes;
        }

        @Override
        public List<MemoryRegion> maps() {
            return List.of();
        }

        @Override
        public void close() {
        }
    }
}
