package com.github.fmaiassistent.memory;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessMemoryReaderTest {
    @Test
    void acceptsAValidUserAddressRange() {
        assertThatCode(() -> ProcessMemoryReader.validateAddressRange(0x1000, 64))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativeAndOverflowingNativeRanges() {
        assertThatThrownBy(() -> ProcessMemoryReader.validateAddressRange(-1, 1))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ProcessMemoryReader.validateAddressRange(
                ProcessMemoryReader.MAX_USER_ADDRESS, 2))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ProcessMemoryReader.validateAddressRange(1, -1))
                .isInstanceOf(IOException.class);
    }
}
