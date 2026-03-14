package com.borko.rajkovic.pizip.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PiChunk} serialization, deserialization, and validation.
 *
 * We use AssertJ for fluent assertions and JUnit 5 @Nested classes to group
 * related scenarios — makes failures easier to diagnose at a glance.
 */
@DisplayName("PiChunk")
class PiChunkTest {

    // ── Pi-reference chunks ───────────────────────────────────────────────────

    @Nested
    @DisplayName("PI_REFERENCE chunks")
    class PiReferenceChunks {

        @Test
        @DisplayName("toString produces 'piIndex:length:offset' format")
        void toStringShouldProducePiReferenceFormat() {
            PiChunk chunk = new PiChunk(48023, 9, 0);
            assertThat(chunk.toString()).isEqualTo("48023:9:0");
        }

        @Test
        @DisplayName("toString includes non-zero offset")
        void toStringShouldIncludeOffset() {
            PiChunk chunk = new PiChunk(1000, 12, 3);
            assertThat(chunk.toString()).isEqualTo("1000:12:3");
        }

        @Test
        @DisplayName("fromString parses 'piIndex:length:offset' correctly")
        void fromStringShouldParsePiReference() {
            PiChunk chunk = PiChunk.fromString("48023:9:0");

            assertThat(chunk.type).isEqualTo(PiChunk.Type.PI_REFERENCE);
            assertThat(chunk.piIndex).isEqualTo(48023);
            assertThat(chunk.length).isEqualTo(9);
            assertThat(chunk.offset).isEqualTo(0);
        }

        @Test
        @DisplayName("fromString defaults offset to 0 when omitted")
        void fromStringShouldDefaultOffsetToZero() {
            PiChunk chunk = PiChunk.fromString("48023:9");
            assertThat(chunk.offset).isEqualTo(0);
        }

        @Test
        @DisplayName("round-trip serialization preserves all fields")
        void roundTripShouldPreserveAllFields() {
            PiChunk original = new PiChunk(123456, 15, 0);
            PiChunk restored = PiChunk.fromString(original.toString());

            assertThat(restored.type).isEqualTo(PiChunk.Type.PI_REFERENCE);
            assertThat(restored.piIndex).isEqualTo(original.piIndex);
            assertThat(restored.length).isEqualTo(original.length);
            assertThat(restored.offset).isEqualTo(original.offset);
        }
    }

    // ── Literal chunks ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LITERAL chunks")
    class LiteralChunks {

        @Test
        @DisplayName("toString produces 'L:' + hex format")
        void toStringShouldProduceLiteralFormat() {
            // 'H' = 0x48, 'i' = 0x69
            PiChunk chunk = new PiChunk(new byte[]{0x48, 0x69});
            assertThat(chunk.toString()).isEqualTo("L:4869");
        }

        @Test
        @DisplayName("toString zero-pads single-digit hex values")
        void toStringShouldZeroPadHex() {
            // byte 0x0A = 10 decimal → should be "0a" not "a"
            PiChunk chunk = new PiChunk(new byte[]{0x0A});
            assertThat(chunk.toString()).isEqualTo("L:0a");
        }

        @Test
        @DisplayName("fromString parses 'L:hex' correctly")
        void fromStringShouldParseLiteral() {
            PiChunk chunk = PiChunk.fromString("L:4869");

            assertThat(chunk.type).isEqualTo(PiChunk.Type.LITERAL);
            assertThat(chunk.literalBytes).containsExactly(0x48, 0x69);
        }

        @Test
        @DisplayName("round-trip serialization preserves bytes exactly")
        void roundTripShouldPreserveBytes() {
            byte[] original = {72, 101, 108, 108, 111}; // "Hello"
            PiChunk chunk   = new PiChunk(original);
            PiChunk restored = PiChunk.fromString(chunk.toString());

            assertThat(restored.literalBytes).containsExactly(original);
        }

        @Test
        @DisplayName("constructor makes defensive copy of byte array")
        void constructorShouldDefensivelyCopyBytes() {
            byte[] bytes = {1, 2, 3};
            PiChunk chunk = new PiChunk(bytes);
            bytes[0] = 99; // mutate original array
            assertThat(chunk.literalBytes[0]).isEqualTo((byte) 1); // chunk unaffected
        }
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromString error cases")
    class ErrorCases {

        @ParameterizedTest
        @DisplayName("throws on blank or null input")
        @ValueSource(strings = {"", "   "})
        void fromStringShouldThrowOnBlankInput(String input) {
            assertThatThrownBy(() -> PiChunk.fromString(input))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on null input")
        void fromStringShouldThrowOnNullInput() {
            assertThatThrownBy(() -> PiChunk.fromString(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on malformed Pi-reference (missing length)")
        void fromStringShouldThrowOnMissingLength() {
            assertThatThrownBy(() -> PiChunk.fromString("48023"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on negative piIndex")
        void fromStringShouldThrowOnNegativePiIndex() {
            assertThatThrownBy(() -> PiChunk.fromString("-1:9:0"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on odd-length hex literal")
        void fromStringShouldThrowOnOddHex() {
            assertThatThrownBy(() -> PiChunk.fromString("L:abc"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
