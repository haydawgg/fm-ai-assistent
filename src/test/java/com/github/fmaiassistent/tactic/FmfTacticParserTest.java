package com.github.fmaiassistent.tactic;

import io.airlift.compress.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FmfTacticParserTest {
    private final FmfTacticParser parser = new FmfTacticParser();

    @Test
    void decryptsAndDecodesTacticResourceFromFmfArchive() {
        var metadata = parser.parse(fmf("4-2-4-press"));

        assertThat(metadata.internalName()).isEqualTo("4-2-4-press");
        assertThat(metadata.resources()).containsExactly("4-2-4-press.tac");
        assertThat(metadata.tactic().name()).isEqualTo("4-2-4-press");
        assertThat(metadata.tactic().tacticalStyle()).isEqualTo("Custom Wing Play");
        assertThat(metadata.tactic().mentality()).isEqualTo("Positive");
        assertThat(metadata.tactic().passingDirectness()).isEqualTo("Shorter");
        assertThat(metadata.tactic().attackingTransition()).isEqualTo("Standard");
        assertThat(metadata.tactic().attackingWidth()).isEqualTo("Wider");
        assertThat(metadata.tactic().creativeFreedom()).isEqualTo("Balanced");
        assertThat(metadata.tactic().timeWasting()).isEqualTo("Standard");
        assertThat(metadata.tactic().inPossession().getFirst().description())
                .isEqualTo("Ball-Playing Goalkeeper (Support)");
        assertThat(metadata.tactic().outOfPossession().getFirst().description())
                .isEqualTo("Sweeper Keeper (Attack)");
    }

    @Test
    void rejectsNonFmfData() {
        assertThatThrownBy(() -> parser.parse("not-an-fmf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a supported");
    }

    @Test
    void prefersRootTacticMatchingArchiveNameOverNestedDecoy() {
        var metadata = parser.parse(fmfNamedRootWithNestedDecoy("4-2-4-press", "decoy"));

        assertThat(metadata.tactic().name()).isEqualTo("4-2-4-press");
        assertThat(metadata.resources()).containsExactly("4-2-4-press.tac", "extras/decoy.tac");
    }

    @Test
    void prefersNamedTacticEvenWhenADecoyAppearsFirst() {
        var metadata = parser.parse(fmfDecoyFirstThenNamedNested("4-2-4-press", "decoy"));

        assertThat(metadata.tactic().name()).isEqualTo("4-2-4-press");
        assertThat(metadata.resources()).containsExactly("decoy.tac", "extras/4-2-4-press.tac");
    }

    @Test
    void rejectsAmbiguousUnmatchedTactics() {
        assertThatThrownBy(() -> parser.parse(fmfTwoRootTactics("bundle", "alpha", "bravo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple tactic resources")
                .hasMessageContaining("alpha.tac")
                .hasMessageContaining("bravo.tac");
    }

    static byte[] fmf(String name) {
        EncryptedResource resource = encryptResource(tactic(name));
        ByteArrayOutputStream catalog = new ByteArrayOutputStream();
        string(catalog, name);
        writeFile(catalog, name, ".tac", 0, resource.stored().length, resource.rawLength());
        integer(catalog, 0);
        return archive(resource.stored(), catalog.toByteArray());
    }

    static byte[] fmfNamedRootWithNestedDecoy(String name, String decoyName) {
        EncryptedResource matching = encryptResource(tactic(name));
        EncryptedResource decoy = encryptResource(tactic(decoyName));
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.writeBytes(matching.stored());
        data.writeBytes(decoy.stored());
        ByteArrayOutputStream catalog = new ByteArrayOutputStream();
        string(catalog, name);
        writeFile(catalog, name, ".tac", 0, matching.stored().length, matching.rawLength());
        integer(catalog, 1);
        string(catalog, "extras");
        writeFile(catalog, decoyName, ".tac", matching.stored().length, decoy.stored().length, decoy.rawLength());
        integer(catalog, 0);
        return archive(data.toByteArray(), catalog.toByteArray());
    }

    static byte[] fmfDecoyFirstThenNamedNested(String name, String decoyName) {
        EncryptedResource decoy = encryptResource(tactic(decoyName));
        EncryptedResource matching = encryptResource(tactic(name));
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.writeBytes(decoy.stored());
        data.writeBytes(matching.stored());
        ByteArrayOutputStream catalog = new ByteArrayOutputStream();
        string(catalog, name);
        writeFile(catalog, decoyName, ".tac", 0, decoy.stored().length, decoy.rawLength());
        integer(catalog, 1);
        string(catalog, "extras");
        writeFile(catalog, name, ".tac", decoy.stored().length, matching.stored().length, matching.rawLength());
        integer(catalog, 0);
        return archive(data.toByteArray(), catalog.toByteArray());
    }

    static byte[] fmfTwoRootTactics(String rootName, String first, String second) {
        EncryptedResource left = encryptResource(tactic(first));
        EncryptedResource right = encryptResource(tactic(second));
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.writeBytes(left.stored());
        data.writeBytes(right.stored());
        ByteArrayOutputStream catalog = new ByteArrayOutputStream();
        string(catalog, rootName);
        integer(catalog, 2);
        catalogFile(catalog, first, ".tac", 0, left.stored().length, left.rawLength());
        catalogFile(catalog, second, ".tac", left.stored().length, right.stored().length, right.rawLength());
        integer(catalog, 0);
        return archive(data.toByteArray(), catalog.toByteArray());
    }

    private static void writeFile(
            ByteArrayOutputStream catalog,
            String baseName,
            String extension,
            long offset,
            long storedLength,
            long rawLength) {
        integer(catalog, 1);
        catalogFile(catalog, baseName, extension, offset, storedLength, rawLength);
    }

    private static void catalogFile(
            ByteArrayOutputStream catalog,
            String baseName,
            String extension,
            long offset,
            long storedLength,
            long rawLength) {
        string(catalog, baseName);
        string(catalog, extension);
        longValue(catalog, offset);
        longValue(catalog, storedLength);
        longValue(catalog, rawLength);
        catalog.writeBytes(new byte[16]);
    }

    private static byte[] archive(byte[] data, byte[] catalog) {
        byte[] compressedCatalog = compress(catalog);
        int catalogOffset = 26 + data.length;
        byte[] header = new byte[26];
        System.arraycopy(new byte[]{2, 1, 'a', 'f', 'e', '.', 8, 0, 0}, 0, header, 0, 9);
        putLong(header, 9, catalogOffset - 9L);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        archive.writeBytes(header);
        archive.writeBytes(data);
        archive.writeBytes(new byte[]{2, 1, 'f', 'm', 'f', '.', 8, 0, 0});
        archive.writeBytes(compressedCatalog);
        return archive.toByteArray();
    }

    private static EncryptedResource encryptResource(byte[] tactic) {
        byte[] compressedTactic = compress(tactic);
        byte[] key = new byte[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16};
        byte[] iv = new byte[]{
                16, 15, 14, 13, 12, 11, 10, 9,
                8, 7, 6, 5, 4, 3, 2, 1};
        byte[] ciphertext = encrypt(compressedTactic, key, iv);
        ByteArrayOutputStream resource = new ByteArrayOutputStream();
        integer(resource, key.length);
        integer(resource, iv.length);
        resource.writeBytes(key);
        resource.writeBytes(iv);
        resource.writeBytes(ciphertext);
        return new EncryptedResource(resource.toByteArray(), tactic.length);
    }

    private record EncryptedResource(byte[] stored, long rawLength) {
    }

    static byte[] tactic(String name) {
        ByteArrayOutputStream tactic = new ByteArrayOutputStream();
        tactic.writeBytes(new byte[]{
                3, 1, 'c', 'a', 't', '.', 0x22, 0, 0x22, 'B', 0, 0x1a, 3, 0, 1, 2});
        string(tactic, name);
        tactic.writeBytes(new byte[12]);
        tactic.writeBytes(new byte[]{4, 2, 5, 6, 2, 3});
        tactic.writeBytes(new byte[12]);
        tactic.write(0xff);
        string(tactic, "Custom Wing Play");
        tactic.writeBytes(new byte[]{'G', 'N', 'I', 'W'});
        role(tactic, 1, 4096L | 0x400000L);
        role(tactic, 1, 2L | 0x800000L);
        return tactic.toByteArray();
    }

    private static void role(ByteArrayOutputStream output, int position, long selection) {
        output.writeBytes(new byte[]{'B', 0, 2});
        integer(output, position);
        output.writeBytes(new byte[]{(byte) 0xff, 0, 1, 1});
        integer(output, 0);
        integer(output, 0);
        longValue(output, selection);
    }

    private static byte[] compress(byte[] bytes) {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (ZstdOutputStream output = new ZstdOutputStream(compressed)) {
            output.write(bytes);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return compressed.toByteArray();
    }

    private static byte[] encrypt(byte[] bytes, byte[] key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(bytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void string(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        integer(output, bytes.length);
        output.writeBytes(bytes);
    }

    private static void integer(ByteArrayOutputStream output, int value) {
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array());
    }

    private static void longValue(ByteArrayOutputStream output, long value) {
        output.writeBytes(ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(value)
                .array());
    }

    private static void putLong(byte[] target, int offset, long value) {
        ByteBuffer.wrap(target, offset, Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(value);
    }
}
