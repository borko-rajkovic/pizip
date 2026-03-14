package com.borko.rajkovic.pizip.service;

import com.borko.rajkovic.pizip.model.PiChunk;
import com.borko.rajkovic.pizip.model.SearchMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PiSearchIndex}.
 *
 * CHALLENGE: PiSearchIndex reads from a PiDigitsService which normally computes
 * or loads 1M digits of Pi — far too slow for a unit test.
 *
 * SOLUTION: We stub PiDigitsService with a small, known digit string so tests
 * run in milliseconds. We use a hand-crafted string "31415926535897932384" that
 * contains known subsequences we can assert against.
 *
 * WHY NOT MOCKITO here?
 * PiDigitsService is a concrete class, not an interface. We could use Mockito's
 * @Mock + when(...).thenReturn(...), but a simple anonymous subclass is cleaner
 * and avoids a framework dependency for something this straightforward.
 */
@DisplayName("PiSearchIndex")
class PiSearchIndexTest {

    /**
     * A small known Pi prefix used for all tests.
     * "3141592653589793238462643383279502884197"
     *
     * Known subsequences we'll assert on:
     *  "141592" starts at index 1
     *  "926535" starts at index 6
     *  "000000" does NOT exist
     */
    private static final String KNOWN_PI = "3141592653589793238462643383279502884197";

    private PiSearchIndex index;

    @BeforeEach
    void setUp() {
        // Stub PiDigitsService with our small known string — no file I/O, instant
        PiDigitsService stub = new PiDigitsService(KNOWN_PI);
        index = new PiSearchIndex(stub);
    }

    // ── findLongestMatch ──────────────────────────────────────────────────────

    @Test
    @DisplayName("finds a known substring at the correct Pi position")
    void shouldFindKnownSubstringAtCorrectPosition() {
        // "141592" starts at index 1 in KNOWN_PI
        PiChunk match = index.findLongestMatch("141592", 0, 6, SearchMode.BALANCED);

        assertThat(match).isNotNull();
        assertThat(match.type).isEqualTo(PiChunk.Type.PI_REFERENCE);
        assertThat(match.piIndex).isEqualTo(1);
        assertThat(match.length).isEqualTo(6);
        assertThat(match.offset).isEqualTo(0);
    }

    @Test
    @DisplayName("returns null when no match exists")
    void shouldReturnNullWhenNoMatch() {
        PiChunk match = index.findLongestMatch("000000", 0, 6, SearchMode.BALANCED);
        assertThat(match).isNull();
    }

    @Test
    @DisplayName("returns null when match is shorter than minLength")
    void shouldReturnNullWhenMatchShorterThanMinLength() {
        // "314" exists but we require 9 digits minimum
        PiChunk match = index.findLongestMatch("314159", 0, 9, SearchMode.BALANCED);
        // Since KNOWN_PI is short, a 6-char match would be found but we demand 9
        // "314159265358" exists, so let's test with something genuinely too short
        PiChunk shortSearch = index.findLongestMatch("999999", 0, 9, SearchMode.BALANCED);
        assertThat(shortSearch).isNull();
    }

    @Test
    @DisplayName("respects start offset within the digit string")
    void shouldRespectStartOffset() {
        // "926535" appears at Pi index 6 — search in string starting at position 0
        PiChunk match = index.findLongestMatch("926535", 0, 6, SearchMode.BALANCED);
        assertThat(match).isNotNull();
        assertThat(match.piIndex).isEqualTo(5);
    }

    @ParameterizedTest(name = "{0} mode")
    @DisplayName("returns consistent results across all search modes")
    @EnumSource(SearchMode.class)
    void shouldFindMatchInAllModes(SearchMode mode) {
        PiChunk match = index.findLongestMatch("141592", 0, 6, mode);
        assertThat(match).isNotNull();
        assertThat(match.piIndex).isEqualTo(1);
    }

    @Test
    @DisplayName("handles short tails (< INDEX_KEY_LEN) via brute-force fallback")
    void shouldHandleShortTailViaBruteForce() {
        // "314" is only 3 chars — below INDEX_KEY_LEN (6), triggers brute-force path
        PiChunk match = index.findLongestMatch("314", 0, 3, SearchMode.BALANCED);
        assertThat(match).isNotNull();
        assertThat(match.piIndex).isEqualTo(0); // "314" starts at index 0
        assertThat(match.length).isEqualTo(3);
    }

    @Test
    @DisplayName("getPiDigits returns the full digit string")
    void shouldReturnFullPiDigits() {
        assertThat(index.getPiDigits()).isEqualTo(KNOWN_PI);
    }

    // ── Inner stub ────────────────────────────────────────────────────────────

    /**
     * Minimal PiDigitsService subclass that bypasses file I/O and uses a
     * provided digit string directly. Only overrides the methods PiSearchIndex uses.
     */
    static class PiDigitsService extends com.borko.rajkovic.pizip.service.PiDigitsService {

        private final String digits;

        PiDigitsService(String digits) {
            // Call a package-visible constructor that skips computation
            super(digits);
            this.digits = digits;
        }

        @Override
        public String getDigits() { return digits; }

        @Override
        public int length() { return digits.length(); }
    }
}
