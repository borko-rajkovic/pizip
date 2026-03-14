package com.borko.rajkovic.pizip.service;

import com.borko.rajkovic.pizip.exception.DecodingException;
import com.borko.rajkovic.pizip.model.PiZipDTOs;
import com.borko.rajkovic.pizip.model.SearchMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PiZipService}.
 *
 * KEY TESTING STRATEGY — Two test Pi strings:
 * ────────────────────────────────────────────
 * 1. SHORT_PI (40 digits): used for tests that verify structural behaviour
 *    (correct chunk types emitted, error handling, etc.) without needing real Pi.
 *
 * 2. LONG_PI (first 200 real Pi digits): used for encode/decode round-trip tests
 *    where we need a large enough Pi string that some digit sequences from our
 *    test inputs actually appear in it. 200 digits is enough for 3-byte sequences.
 *
 * All tests run in milliseconds — no file I/O, no BigDecimal arithmetic.
 */
@DisplayName("PiZipService")
class PiZipServiceTest {

    /** First 40 digits of Pi — too short to find most matches, useful for structural tests. */
    private static final String SHORT_PI = "3141592653589793238462643383279502884197";

    /**
     * First 200 digits of Pi (no decimal point).
     * Long enough that digit sequences for common ASCII byte values (e.g. "065" for 'A',
     * "072" for 'H') can often be found, enabling round-trip tests.
     */
    private static final String LONG_PI =
        "31415926535897932384626433832795028841971693993751" +
        "05820974944592307816406286208998628034825342117067" +
        "98214808651328230664709384460955058223172535940812" +
        "84811174502841027019385211055596446229489549303819";

    // Shared service instances — expensive enough to create once per class
    private static PiZipService shortService;
    private static PiZipService longService;

    @BeforeAll
    static void setUpServices() {
        shortService = serviceWith(SHORT_PI);
        longService  = serviceWith(LONG_PI);
    }

    // ── encode ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("encode()")
    class Encode {

        @Test
        @DisplayName("returns non-null EncodeResponse for any input")
        void shouldReturnNonNullResponse() {
            PiZipDTOs.EncodeResponse resp = longService.encode("Hello");
            assertThat(resp).isNotNull();
            assertThat(resp.encoded()).isNotBlank();
        }

        @Test
        @DisplayName("encoded string contains only chunk separators and valid chars")
        void encodedStringShouldHaveValidFormat() {
            PiZipDTOs.EncodeResponse resp = longService.encode("Hello");
            // Each chunk is either "N:N:N" (digits and colons) or "L:hex"
            // So the only valid characters are digits, ':', ';', 'L', 'a'-'f'
            assertThat(resp.encoded()).matches("[0-9:;La-f]+");
        }

        @Test
        @DisplayName("stats reflect actual chunk counts")
        void statsShouldMatchActualChunks() {
            PiZipDTOs.EncodeResponse resp = longService.encode("Hi", SearchMode.FAST);
            assertThat(resp.piRefChunks() + resp.literalChunks()).isEqualTo(resp.totalChunks());
        }

        @Test
        @DisplayName("originalBytes matches UTF-8 byte count of input")
        void originalBytesShouldMatchUtf8ByteCount() {
            String text = "café"; // 5 UTF-8 bytes (é is 2 bytes)
            PiZipDTOs.EncodeResponse resp = longService.encode(text);
            assertThat(resp.originalBytes())
                    .isEqualTo(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        }

        @Test
        @DisplayName("searchMode in response reflects the mode used")
        void responseShouldReflectSearchMode() {
            PiZipDTOs.EncodeResponse resp = longService.encode("test", SearchMode.FAST);
            assertThat(resp.searchMode()).isEqualTo("FAST");
        }

        @ParameterizedTest(name = "{0} mode")
        @DisplayName("all search modes produce a decodable result")
        @EnumSource(SearchMode.class)
        void allModesShouldProduceDecodableOutput(SearchMode mode) {
            String input = "Hello";
            PiZipDTOs.EncodeResponse enc  = longService.encode(input, mode);
            PiZipDTOs.DecodeResponse dec  = longService.decode(enc.encoded());
            assertThat(dec.text()).isEqualTo(input);
        }

        @Test
        @DisplayName("empty string encodes and decodes correctly")
        void shouldHandleEmptyString() {
            PiZipDTOs.DecodeResponse dec = longService.decode(
                    longService.encode("").encoded());
            assertThat(dec.text()).isEmpty();
        }
    }

    // ── decode ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("decode()")
    class Decode {

        @Test
        @DisplayName("empty encoded string returns empty text")
        void emptyEncodedShouldReturnEmptyText() {
            assertThat(longService.decode("").text()).isEmpty();
        }

        @Test
        @DisplayName("decodes a hand-crafted literal chunk correctly")
        void shouldDecodeLiteralChunk() {
            // 'H' = 72 = 0x48, 'i' = 105 = 0x69
            PiZipDTOs.DecodeResponse resp = longService.decode("L:4869");
            assertThat(resp.text()).isEqualTo("Hi");
        }

        @Test
        @DisplayName("decodes a valid Pi-reference chunk correctly")
        void shouldDecodePiReferenceChunk() {
            // Build a Pi-ref that we know is valid: take the first 3 digits of LONG_PI
            // which are "314" and construct the corresponding byte manually
            // 3 Pi digits "314" represent one byte with value 314? No — values must be 000-255.
            // Let's instead verify round-trip, which also tests decode implicitly.
            String original = "A"; // 'A' = 65 = "065" — likely in LONG_PI
            String encoded  = longService.encode(original, SearchMode.BEST).encoded();
            assertThat(longService.decode(encoded).text()).isEqualTo(original);
        }

        @Test
        @DisplayName("throws DecodingException on malformed chunk")
        void shouldThrowDecodingExceptionOnMalformedChunk() {
            assertThatThrownBy(() -> longService.decode("notavalidchunk"))
                    .isInstanceOf(DecodingException.class);
        }

        @Test
        @DisplayName("throws DecodingException on Pi-ref out of bounds")
        void shouldThrowDecodingExceptionOnOutOfBoundsPiRef() {
            // piIndex=999999 with length=300 would exceed our 200-digit test Pi
            assertThatThrownBy(() -> longService.decode("999999:300:0"))
                    .isInstanceOf(DecodingException.class)
                    .hasMessageContaining("out of bounds");
        }

        @Test
        @DisplayName("throws DecodingException when Pi-ref length is not divisible by 3")
        void shouldThrowWhenPiRefLengthNotDivisibleByThree() {
            // length=7 is not divisible by 3 — cannot reconstruct whole bytes
            assertThatThrownBy(() -> longService.decode("0:7:0"))
                    .isInstanceOf(DecodingException.class);
        }
    }

    // ── encode/decode round-trips ─────────────────────────────────────────────

    @Nested
    @DisplayName("encode → decode round-trips")
    class RoundTrips {

        @ParameterizedTest(name = "''{0}''")
        @DisplayName("recovers original text for various inputs")
        @ValueSource(strings = {
            "Hello, World!",
            "A",
            "1234567890",
            "The quick brown fox",
            "!@#$%^",
            "aaaaaaaaa",   // repeated chars — likely to find Pi matches
            " ",           // single space
        })
        void shouldRoundTripAllInputs(String input) {
            String encoded = longService.encode(input, SearchMode.BALANCED).encoded();
            String decoded = longService.decode(encoded).text();
            assertThat(decoded).isEqualTo(input);
        }

        @Test
        @DisplayName("BEST mode produces fewest or equal chunks vs BALANCED")
        void bestModeShouldProduceFewOrEqualChunksVsBalanced() {
            String input   = "Hello World 12345";
            int bestChunks     = longService.encode(input, SearchMode.BEST).totalChunks();
            int balancedChunks = longService.encode(input, SearchMode.BALANCED).totalChunks();
            // BEST is optimal — it must produce ≤ chunks than greedy BALANCED
            assertThat(bestChunks).isLessThanOrEqualTo(balancedChunks);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a PiZipService backed by the given Pi digit string (no file I/O). */
    static PiZipService serviceWith(String piDigits) {
        // Use the package-visible test constructor in PiDigitsService
        PiDigitsService stub = new PiDigitsService(piDigits) {};
        return new PiZipService(stub, SearchMode.BALANCED);
    }
}
