package com.borko.rajkovic.pizip.service;

import com.borko.rajkovic.pizip.exception.DecodingException;
import com.borko.rajkovic.pizip.model.PiChunk;
import com.borko.rajkovic.pizip.model.PiZipDTOs;
import com.borko.rajkovic.pizip.model.SearchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * PiZipService — The main application service for encoding and decoding.
 *
 * This is the primary bean that application code and the REST controller interact with.
 * It orchestrates:
 *  - {@link com.borko.rajkovic.pizip.service.PiSearchIndex} for fast Pi substring lookup
 *  - Greedy and DP encoding algorithms
 *  - Chunk-based decoding
 *
 * All public methods are stateless with respect to the input — the service holds
 * only the shared, immutable Pi index and is safe to call from multiple threads.
 *
 * ── ENCODING ALGORITHM ──────────────────────────────────────────────────────
 *
 * 1. Convert input → UTF-8 bytes → 3-digit-per-byte fixed-width digit string.
 *    ("Hi" → [72, 105] → "072105")
 *
 * 2. Slide across the digit string:
 *    a. Look up the longest Pi match starting at the current position.
 *    b. If a match longer than MIN_MATCH_DIGITS is found → emit a Pi-ref chunk.
 *    c. Otherwise → emit a literal chunk and advance by one byte (3 digits).
 *
 * 3. In BEST mode, use dynamic programming instead of greedy to find the
 *    globally optimal (fewest chunks) encoding.
 *
 * ── DECODING ALGORITHM ──────────────────────────────────────────────────────
 *
 * For each chunk:
 *  - Pi-ref  → fetch piDigits.substring(piIndex + offset, ... + length)
 *  - Literal → use the raw bytes directly
 * Concatenate all recovered bytes → UTF-8 decode → original string.
 */
@Service
public class PiZipService {

    private static final Logger log = LoggerFactory.getLogger(PiZipService.class);

    /**
     * Minimum Pi-match length before we prefer a Pi-reference over a literal.
     *
     * A Pi-reference like "48023:9:0" costs ~9 characters in the encoded string.
     * A literal for 3 bytes costs "L:xxxxxx" = 8 characters.
     * So a Pi-ref only wins when the match covers ≥ 9 digit-characters (= 3 bytes).
     *
     * Break-even analysis:
     *  Pi-ref length  = len("piIndex") + 1 + len("length") + 1 + 1 ≈ 9–14 chars
     *  Literal length = 2 + 2*numBytes chars
     *
     * We use 9 as a conservative threshold; tune upward to trade compression for speed.
     */
    static final int MIN_MATCH_DIGITS = 9;

    private final com.borko.rajkovic.pizip.service.PiSearchIndex searchIndex;
    private final SearchMode    defaultMode;

    public PiZipService(
            PiDigitsService piDigitsService,
            @Value("${pizip.default-search-mode}") SearchMode defaultMode
    ) {
        this.searchIndex = new com.borko.rajkovic.pizip.service.PiSearchIndex(piDigitsService);
        this.defaultMode = defaultMode;
        log.info("PiZipService ready: default mode: {}", defaultMode);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Encode the given plaintext using the service's default search mode.
     *
     * @param text Plaintext to encode (any valid UTF-8 string).
     * @return {@link PiZipDTOs.EncodeResponse} with encoded string and statistics.
     */
    public PiZipDTOs.EncodeResponse encode(String text) {
        return encode(text, defaultMode);
    }

    /**
     * Encode the given plaintext using the specified search mode.
     *
     * @param text Plaintext to encode.
     * @param mode Search mode — FAST, BALANCED, or BEST.
     * @return {@link PiZipDTOs.EncodeResponse} with encoded string and statistics.
     */
    public PiZipDTOs.EncodeResponse encode(String text, SearchMode mode) {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(mode, "mode must not be null");

        byte[] inputBytes = text.getBytes(StandardCharsets.UTF_8);
        String digitStr   = bytesToDigitString(inputBytes);

        log.debug("Encoding {} bytes ({} digits) in {} mode", inputBytes.length, digitStr.length(), mode);

        List<PiChunk> chunks = (mode == SearchMode.BEST)
                ? encodeWithDP(digitStr)
                : encodeGreedy(digitStr, mode);

        // Serialize chunks to the wire format
        StringJoiner joiner = new StringJoiner(";");
        int piRefs = 0, literals = 0;
        for (PiChunk chunk : chunks) {
            joiner.add(chunk.toString());
            if (chunk.type == PiChunk.Type.PI_REFERENCE) piRefs++; else literals++;
        }
        String encoded = joiner.toString();

        int origBytes  = inputBytes.length;
        int encLen     = encoded.length();
        double ratio   = origBytes > 0 ? (double) encLen / origBytes : 0;

        log.debug("Encoded {} bytes → {} chars ({} chunks: {} pi-refs, {} literals, ratio {:.2f})",
                origBytes, encLen, chunks.size(), piRefs, literals, ratio);

        return new PiZipDTOs.EncodeResponse(
                encoded, text, origBytes, encLen, ratio, piRefs, literals, chunks.size(), mode.name());
    }

    /**
     * Decode an encoded string back to the original plaintext.
     *
     * @param encoded Encoded string produced by {@link #encode(String, SearchMode)}.
     * @return {@link PiZipDTOs.DecodeResponse} with the recovered text.
     * @throws DecodingException if the encoded string is malformed or references
     *                           Pi positions out of bounds.
     */
    public PiZipDTOs.DecodeResponse decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded must not be null");
        if (encoded.isBlank()) return new PiZipDTOs.DecodeResponse("");

        String piDigits = searchIndex.getPiDigits();
        String[] tokens = encoded.split(";");

        List<Byte> allBytes = new ArrayList<>();

        for (String token : tokens) {
            PiChunk chunk;
            try {
                chunk = PiChunk.fromString(token.trim());
            } catch (IllegalArgumentException e) {
                throw new DecodingException("Malformed chunk '" + token + "': " + e.getMessage(), e);
            }

            if (chunk.type == PiChunk.Type.PI_REFERENCE) {
                int start = chunk.piIndex + chunk.offset;
                int end   = start + chunk.length;

                if (end > piDigits.length()) {
                    throw new DecodingException(
                        "Pi reference out of bounds: piIndex=" + chunk.piIndex
                        + " offset=" + chunk.offset + " length=" + chunk.length
                        + " (only " + piDigits.length() + " Pi digits available)");
                }
                if (chunk.length % 3 != 0) {
                    throw new DecodingException(
                        "Pi-reference chunk length must be divisible by 3 (3 digits per byte), got: "
                        + chunk.length + " at piIndex=" + chunk.piIndex);
                }

                byte[] segBytes = digitStringToBytes(piDigits.substring(start, end));
                for (byte b : segBytes) allBytes.add(b);

            } else {
                for (byte b : chunk.literalBytes) allBytes.add(b);
            }
        }

        byte[] byteArray = new byte[allBytes.size()];
        for (int i = 0; i < byteArray.length; i++) byteArray[i] = allBytes.get(i);

        return new PiZipDTOs.DecodeResponse(new String(byteArray, StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Greedy encoding (FAST / BALANCED)
    // ─────────────────────────────────────────────────────────────────────────

    private List<PiChunk> encodeGreedy(String digitStr, SearchMode mode) {
        List<PiChunk> chunks   = new ArrayList<>();
        int pos                = 0;
        int totalLen           = digitStr.length();

        while (pos < totalLen) {
            PiChunk match = searchIndex.findLongestMatch(digitStr, pos, MIN_MATCH_DIGITS, mode);

            if (match != null) {
                chunks.add(match);
                pos += match.length;
            } else {
                // Batch unmatched digits: look ahead up to 10 bytes (30 digits) to find
                // the next matchable position, then emit everything in between as one literal.
                int literalEnd = findNextMatchablePosition(digitStr, pos, mode);
                chunks.add(new PiChunk(digitStringToBytes(digitStr.substring(pos, literalEnd))));
                pos = literalEnd;
            }
        }

        return chunks;
    }

    /**
     * Look ahead to find the next position (aligned to a 3-digit byte boundary) where
     * a Pi match exists. This lets us batch consecutive unmatched bytes into a single
     * literal chunk instead of emitting one tiny literal per byte.
     */
    private int findNextMatchablePosition(String digitStr, int pos, SearchMode mode) {
        int totalLen = digitStr.length();
        int nextPos  = pos + 3; // advance at least one byte
        if (nextPos >= totalLen) return totalLen;

        int lookAheadLimit = Math.min(pos + 30, totalLen - 3); // look up to 10 bytes ahead
        for (int p = nextPos; p <= lookAheadLimit; p += 3) {
            if (searchIndex.findLongestMatch(digitStr, p, MIN_MATCH_DIGITS, mode) != null) {
                return p;
            }
        }
        return nextPos; // give up, advance by one byte
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dynamic programming encoding (BEST mode)
    //
    // dp[i] = minimum chunks to encode digitStr[0..i)
    // For each position, try all Pi matches + a literal fallback.
    // Reconstruct the optimal path by backtracking through the 'from' array.
    // ─────────────────────────────────────────────────────────────────────────

    private List<PiChunk> encodeWithDP(String digitStr) {
        int        n       = digitStr.length();
        int[]      dp      = new int[n + 1];
        PiChunk[]  from    = new PiChunk[n + 1];
        int[]      fromPos = new int[n + 1];

        Arrays.fill(dp, Integer.MAX_VALUE / 2);
        dp[0] = 0;

        for (int i = 0; i < n; i++) {
            if (dp[i] >= Integer.MAX_VALUE / 2) continue;

            // Option A: best Pi-reference match starting at i
            PiChunk match = searchIndex.findLongestMatch(digitStr, i, MIN_MATCH_DIGITS, SearchMode.BEST);
            if (match != null && dp[i] + 1 < dp[i + match.length]) {
                dp[i + match.length] = dp[i] + 1;
                from[i + match.length]    = match;
                fromPos[i + match.length] = i;
            }

            // Option B: literal for one byte (always available)
            int literalEnd = Math.min(i + 3, n);
            PiChunk lit    = new PiChunk(digitStringToBytes(digitStr.substring(i, literalEnd)));
            if (dp[i] + 1 < dp[literalEnd]) {
                dp[literalEnd]      = dp[i] + 1;
                from[literalEnd]    = lit;
                fromPos[literalEnd] = i;
            }
        }

        // Reconstruct optimal path by walking backwards from dp[n]
        LinkedList<PiChunk> chunks = new LinkedList<>();
        int pos = n;
        while (pos > 0) {
            chunks.addFirst(from[pos]);
            pos = fromPos[pos];
        }
        return new ArrayList<>(chunks);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Digit string utilities (package-visible for tests)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a byte array to a fixed-width decimal digit string.
     * Each byte maps to exactly 3 digits (zero-padded), so the output
     * length is always {@code bytes.length * 3}.
     *
     * <p>Fixed-width is essential for unambiguous decoding — variable-width
     * strings like "72105" could mean [7, 21, 5] or [72, 105], but
     * "072105" can only mean [072, 105] = [72, 105].
     *
     * @param bytes Unsigned byte values (0–255 interpreted as unsigned).
     * @return Fixed-width digit string, e.g. [72, 10, 200] → "072010200".
     */
    public static String bytesToDigitString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            sb.append(String.format("%03d", Byte.toUnsignedInt(b)));
        }
        return sb.toString();
    }

    /**
     * Converts a fixed-width digit string back to bytes.
     * The string length must be a multiple of 3.
     *
     * @throws IllegalArgumentException if the length is not divisible by 3.
     */
    public static byte[] digitStringToBytes(String digits) {
        if (digits.length() % 3 != 0) {
            throw new IllegalArgumentException(
                "Digit string length must be a multiple of 3, got: " + digits.length());
        }
        byte[] bytes = new byte[digits.length() / 3];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(digits, i * 3, i * 3 + 3, 10);
        }
        return bytes;
    }
}
