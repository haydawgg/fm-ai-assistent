package com.github.fmaiassistent.exporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerExporterSalaryTest {
    @Test
    void missingContractRegistrationIsUnknownWageNotZero() {
        PlayerExporter.Salary unknown = PlayerExporter.salaryFromWeeklyRaw(null);
        assertNull(unknown.weeklyRaw());
        assertNull(unknown.annualRounded());
    }

    @Test
    void zeroWeeklyWageIsPreservedWhenAContractWasRead() {
        PlayerExporter.Salary zero = PlayerExporter.salaryFromWeeklyRaw(0L);
        assertEquals(0L, zero.weeklyRaw());
        assertEquals(0L, zero.annualRounded());
    }
}
