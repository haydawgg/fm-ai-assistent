package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.linux.GamePluginIdentity;
import com.github.fmaiassistent.memory.ProcessMemoryReader;

import java.io.IOException;
import java.util.Map;

/**
 * Reads optional fields only from an explicitly registered build/module layout.
 * The default registry is empty because external offset tables are not trusted
 * until they have been validated against a live FM build.
 */
public final class BuildPlayerFieldReader implements PlayerFieldReader {
    private final Map<Integer, Layout> buildLayouts;
    private final Map<BuildSeasonStatsReader.ProfileKey, Layout> profileLayouts;

    public BuildPlayerFieldReader() {
        this(Map.of(), Map.of());
    }

    public BuildPlayerFieldReader(Map<Integer, Layout> buildLayouts,
                                  Map<BuildSeasonStatsReader.ProfileKey, Layout> profileLayouts) {
        this.buildLayouts = buildLayouts == null ? Map.of() : Map.copyOf(buildLayouts);
        this.profileLayouts = profileLayouts == null ? Map.of() : Map.copyOf(profileLayouts);
    }

    @Override
    public CandidatePlayerFields read(ProcessMemoryReader reader, int build, long playerRecord,
                                      GamePluginIdentity identity) {
        Layout layout = profileLayouts.get(new BuildSeasonStatsReader.ProfileKey(
                build, identity == null ? "" : identity.sha256()));
        if (layout == null) layout = buildLayouts.get(build);
        if (layout == null || reader == null || playerRecord <= 0) {
            return CandidatePlayerFields.unknown();
        }

        Long uid = readUnsignedInt(reader, playerRecord + layout.sourceUidOffset());
        Integer morale = readUnsignedByte(reader, playerRecord + layout.moraleOffset());
        Integer condition = readUnsignedShort(reader, playerRecord + layout.conditionOffset());
        Long guideValue = readUnsignedInt(reader, playerRecord + layout.guideValueOffset());
        Long transferValue = readUnsignedInt(reader, playerRecord + layout.transferValueOffset());
        CandidatePlayerFields fields = new CandidatePlayerFields(uid, morale, condition, guideValue, transferValue,
                anyNull(uid, morale, condition, guideValue, transferValue)
                        ? CandidatePlayerFields.State.PARTIAL
                        : CandidatePlayerFields.State.AVAILABLE);
        return fields;
    }

    private static boolean anyNull(Object... values) {
        for (Object value : values) if (value == null) return true;
        return false;
    }

    private static Long readUnsignedInt(ProcessMemoryReader reader, long address) {
        try { return reader.readU32(address); } catch (IOException | RuntimeException ex) { return null; }
    }

    private static Integer readUnsignedByte(ProcessMemoryReader reader, long address) {
        try { return reader.readU8(address); } catch (IOException | RuntimeException ex) { return null; }
    }

    private static Integer readUnsignedShort(ProcessMemoryReader reader, long address) {
        try { return reader.readU16(address); } catch (IOException | RuntimeException ex) { return null; }
    }

    /** All offsets are relative to the person record. */
    public record Layout(int sourceUidOffset, int moraleOffset, int conditionOffset,
                         int guideValueOffset, int transferValueOffset) {
    }
}
