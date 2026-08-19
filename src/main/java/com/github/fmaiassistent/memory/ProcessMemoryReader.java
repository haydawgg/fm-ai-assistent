package com.github.fmaiassistent.memory;

import com.github.fmaiassistent.linux.MemoryRegion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public interface ProcessMemoryReader extends AutoCloseable {
    long MAX_USER_ADDRESS = 0x00007FFFFFFFFFFFL;
    Charset FM_SINGLE_BYTE = Charset.forName("windows-1252");
    AtomicLong READ_COUNT = new AtomicLong(0);
    long REGION_CACHE_TTL_NS = TimeUnit.SECONDS.toNanos(30);
    ConcurrentHashMap<Integer, RegionSnapshot> REGION_CACHE = new ConcurrentHashMap<>();

    record RegionSnapshot(List<MemoryRegion> regions, long createdAtNanos) {
    }

    int pid();

    default Platform platform() {
        return Platform.UNKNOWN;
    }

    byte[] readBytes(long address, int size) throws IOException;

    /**
     * Result-oriented read seam for exporters that must preserve unknown and
     * failed fields instead of manufacturing a numeric default.
     */
    default MemoryReadResult<byte[]> readBytesResult(long address, int size) {
        try {
            byte[] bytes = readBytes(address, size);
            return bytes == null ? MemoryReadResult.unknown("Reader returned no bytes") : MemoryReadResult.known(bytes);
        } catch (IOException | RuntimeException ex) {
            return MemoryReadResult.error(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    /** Rejects invalid native ranges before they reach a platform adapter. */
    static void validateAddressRange(long address, int size) throws IOException {
        if (size < 0) {
            throw new IOException("Read size must be non-negative");
        }
        if (size == 0) {
            return;
        }
        if (address <= 0 || address > MAX_USER_ADDRESS
                || (long) size - 1 > MAX_USER_ADDRESS - address) {
            throw new IOException("Read range is outside the user address space");
        }
    }

    List<MemoryRegion> maps() throws IOException;

    private static void throttle() {
        if (READ_COUNT.incrementAndGet() % 1024 == 0) {
            Thread.yield();
        }
    }

    default int readU8(long address) throws IOException {
        throttle();
        return readBytes(address, 1)[0] & 0xff;
    }

    default int readU16(long address) throws IOException {
        throttle();
        return le(readBytes(address, 2)).getShort() & 0xffff;
    }

    default short readI16(long address) throws IOException {
        throttle();
        return le(readBytes(address, 2)).getShort();
    }

    default long readU32(long address) throws IOException {
        throttle();
        return le(readBytes(address, 4)).getInt() & 0xffffffffL;
    }

    default int readI32(long address) throws IOException {
        throttle();
        return le(readBytes(address, 4)).getInt();
    }

    default long readU64(long address) throws IOException {
        throttle();
        return le(readBytes(address, 8)).getLong();
    }

    default Optional<Long> qwordOrNull(long address) {
        if (address <= 0 || address > MAX_USER_ADDRESS) {
            return Optional.empty();
        }
        try {
            long value = readU64(address);
            if (value <= 0 || value > MAX_USER_ADDRESS) {
                return Optional.empty();
            }
            List<MemoryRegion> regions = cachedRegions();
            if (!regions.isEmpty()) {
                boolean contained = false;
                for (MemoryRegion region : regions) {
                    if (value >= region.start() && value < region.end()) {
                        contained = true;
                        break;
                    }
                }
                if (!contained) {
                    return Optional.empty();
                }
            }
            return Optional.of(value);
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    default List<MemoryRegion> cachedRegions() {
        int key = pid();
        long now = System.nanoTime();
        REGION_CACHE.entrySet().removeIf(entry -> now - entry.getValue().createdAtNanos() >= REGION_CACHE_TTL_NS);
        RegionSnapshot snapshot = REGION_CACHE.get(key);
        if (snapshot != null && now - snapshot.createdAtNanos() < REGION_CACHE_TTL_NS) {
            return snapshot.regions();
        }
        try {
            List<MemoryRegion> regions = maps();
            REGION_CACHE.put(key, new RegionSnapshot(regions, now));
            return regions;
        } catch (IOException | RuntimeException ex) {
            return snapshot == null ? List.of() : snapshot.regions();
        }
    }

    default Optional<String> readFmLenString(long address, int maxLen) {
        if (address <= 0 || address > MAX_USER_ADDRESS) {
            return Optional.empty();
        }
        try {
            int size = le(readBytes(address, 4)).getInt();
            if (size <= 0 || size > maxLen) {
                return Optional.empty();
            }
            byte[] data = readBytes(address + 4, size);
            for (byte b : data) {
                int ch = b & 0xff;
                if (ch != 9 && ch != 10 && ch != 13 && ch < 32) {
                    return Optional.empty();
                }
            }
            String result = new String(data, FM_SINGLE_BYTE);
            int end = result.length();
            while (end > 0) {
                char c = result.charAt(end - 1);
                if (c == 0 || Character.isWhitespace(c)) {
                    end--;
                } else {
                    break;
                }
            }
            return Optional.of(result.substring(0, end));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    default Optional<String> readFmStringObject(long address, int maxLen) {
        return qwordOrNull(address).flatMap(target -> readFmLenString(target, maxLen));
    }

    private static ByteBuffer le(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    void close() throws IOException;

    enum Platform {
        LINUX,
        WINDOWS,
        UNKNOWN
    }
}
