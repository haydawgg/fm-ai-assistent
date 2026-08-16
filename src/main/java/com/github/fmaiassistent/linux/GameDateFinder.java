package com.github.fmaiassistent.linux;

import com.github.fmaiassistent.memory.ProcessMemoryReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

public class GameDateFinder {
    private static final Logger log = LoggerFactory.getLogger(GameDateFinder.class);

    /** FM packs extra flag bits above the day-of-year; keep the low 9 bits. */
    public static final int DAY_MASK = 0x01FF;

    public Optional<LocalDate> find(ProcessMemoryReader reader) throws IOException {
        return find(reader, FmOffsets.DEFAULT_BUILD, null);
    }

    public Optional<LocalDate> find(
            ProcessMemoryReader reader,
            int build,
            Long gamePluginBase) {
        Long dateRva = FmOffsets.currentDateRva(build);
        if (dateRva == null) {
            log.warn("Current-date RVA is not known for build 0x{}; the game date will be unavailable",
                    Integer.toHexString(build));
            return Optional.empty();
        }

        try {
            long base = gamePluginBase == null ? FmOffsets.findGamePluginBase(reader) : gamePluginBase;
            int day = maskedDay(reader.readU16(base + dateRva));
            int year = reader.readU16(base + dateRva + Short.BYTES);
            return year >= 2024 && validDayYear(day, year)
                    ? Optional.of(dayYearToDate(day, year))
                    : Optional.empty();
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not read the current game date: {}", ex.toString());
            return Optional.empty();
        }
    }

    public static int maskedDay(int rawDay) {
        return rawDay & DAY_MASK;
    }

    public static boolean validDayYear(int day, int year) {
        if (year < 1901 || year > 2100) {
            return false;
        }
        int maxDay = LocalDate.of(year, 12, 31).getDayOfYear();
        return day >= 1 && day <= maxDay;
    }

    public static LocalDate dayYearToDate(int day, int year) {
        return LocalDate.of(year, 1, 1).plusDays(day - 1L);
    }

    public static int ageYears(LocalDate dateOfBirth, LocalDate on) {
        return Period.between(dateOfBirth, on).getYears();
    }

    public static Integer effectiveAge(String storedAge, String dateOfBirth, String ageAsOf) {
        Integer stored = parseLooseInt(storedAge);
        if (stored != null) {
            return stored;
        }
        LocalDate dob = parseIsoDate(dateOfBirth);
        if (dob == null) {
            return null;
        }
        LocalDate on = parseIsoDate(ageAsOf);
        if (on == null) {
            on = LocalDate.now();
        }
        return ageYears(dob, on);
    }

    public static Integer parseLooseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ex) {
            try {
                double parsed = Double.parseDouble(trimmed);
                if (!Double.isFinite(parsed)) {
                    return null;
                }
                return (int) parsed;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static LocalDate parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}