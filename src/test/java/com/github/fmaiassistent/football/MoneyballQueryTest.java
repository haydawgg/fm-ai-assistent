package com.github.fmaiassistent.football;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyballQueryTest {
    @Test
    void aggregateCopiesItsRows() {
        List<MoneyballCandidate> rows = new ArrayList<>();
        MoneyballAnalysisResult result = new MoneyballAnalysisResult(rows, 0, 0, 0, 0);

        rows.add(null);

        assertEquals(List.of(), result.rows());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
    }
}
