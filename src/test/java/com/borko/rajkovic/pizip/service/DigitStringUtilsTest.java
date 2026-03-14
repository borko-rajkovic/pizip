package com.borko.rajkovic.pizip.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PiZipService#bytesToDigitString} and
 * {@link PiZipService#digitStringToBytes}.
 *
 * These are static utility methods so we can test them without spinning up
 * a Spring context or computing Pi digits — pure unit tests, very fast.
 */
@DisplayName("PiZipService digit string utilities")
class DigitStringUtilsTest {

    // ── bytesToDigitString ────────────────────────────────────────────────────

    @Nested
    @DisplayName("bytesToDigitString")
    class BytesToDigitString {

        @Test
        @DisplayName("encodes each byte as exactly 3 decimal digits")
        void shouldEncodeEachByteAsThreeDigits() {
            // 'H' = 72, 'i' = 105
            byte[] bytes = "Hi".getBytes();
            assertThat(PiZipService.bytesToDigitString(bytes))
                    .isEqualTo("072105");
        }

        @Test
        @DisplayName("zero-pads single and double digit values")
        void shouldZeroPadSmallValues() {
            assertThat(PiZipService.bytesToDigitString(new byte[]{0}))
                    .isEqualTo("000");
            assertThat(PiZipService.bytesToDigitString(new byte[]{9}))
                    .isEqualTo("009");
            assertThat(PiZipService.bytesToDigitString(new byte[]{99}))
                    .isEqualTo("099");
        }

        @Test
        @DisplayName("handles maximum byte value (255 unsigned)")
        void shouldHandleMaxByte() {
            // byte -1 in Java = 0xFF = 255 unsigned
            assertThat(PiZipService.bytesToDigitString(new byte[]{(byte) 255}))
                    .isEqualTo("255");
        }

        @Test
        @DisplayName("output length is always 3x input length")
        void outputLengthShouldBeTripleInput() {
            for (int len = 0; len <= 10; len++) {
                byte[] bytes = new byte[len];
                assertThat(PiZipService.bytesToDigitString(bytes)).hasSize(len * 3);
            }
        }

        @Test
        @DisplayName("empty byte array produces empty string")
        void emptyByteArrayProducesEmptyString() {
            assertThat(PiZipService.bytesToDigitString(new byte[0])).isEmpty();
        }

        @ParameterizedTest(name = "text ''{0}''")
        @DisplayName("round-trips correctly for various strings")
        @MethodSource("com.borko.rajkovic.pizip.service.DigitStringUtilsTest#roundTripSamples")
        void roundTripShouldBeExact(String text) {
            byte[] original = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String digits   = PiZipService.bytesToDigitString(original);
            byte[] restored = PiZipService.digitStringToBytes(digits);
            assertThat(restored).isEqualTo(original);
        }
    }

    // ── digitStringToBytes ────────────────────────────────────────────────────

    @Nested
    @DisplayName("digitStringToBytes")
    class DigitStringToBytes {

        @Test
        @DisplayName("parses three-digit groups back to bytes")
        void shouldParseThreeDigitGroups() {
            byte[] result = PiZipService.digitStringToBytes("072105");
            assertThat(result).containsExactly(72, 105); // 'H', 'i'
        }

        @Test
        @DisplayName("correctly handles 000 → byte 0")
        void shouldHandleZeroByte() {
            assertThat(PiZipService.digitStringToBytes("000"))
                    .containsExactly(0);
        }

        @Test
        @DisplayName("correctly handles 255 → byte -1 (signed) / 255 (unsigned)")
        void shouldHandleMaxByte() {
            byte[] result = PiZipService.digitStringToBytes("255");
            assertThat(Byte.toUnsignedInt(result[0])).isEqualTo(255);
        }

        @Test
        @DisplayName("empty string produces empty byte array")
        void emptyStringProducesEmptyArray() {
            assertThat(PiZipService.digitStringToBytes("")).isEmpty();
        }

        @Test
        @DisplayName("throws IllegalArgumentException when length is not divisible by 3")
        void shouldThrowWhenLengthNotDivisibleByThree() {
            assertThatThrownBy(() -> PiZipService.digitStringToBytes("07"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("multiple of 3");

            assertThatThrownBy(() -> PiZipService.digitStringToBytes("0721"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Test data ─────────────────────────────────────────────────────────────

    static Stream<Arguments> roundTripSamples() {
        return Stream.of(
            Arguments.of("Hello, World!"),
            Arguments.of("A"),
            Arguments.of("1234567890"),
            Arguments.of("Special chars: !@#$%^&*()"),
            Arguments.of("Unicode: café ñoño 日本語"),
            Arguments.of(""),              // empty string — zero bytes
            Arguments.of("\n\t\r"),        // control characters
            Arguments.of("a".repeat(100)) // longer input
        );
    }
}
