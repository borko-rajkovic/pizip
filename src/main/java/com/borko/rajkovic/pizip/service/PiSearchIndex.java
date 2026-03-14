package com.borko.rajkovic.pizip.service;

import com.borko.rajkovic.pizip.model.PiChunk;
import com.borko.rajkovic.pizip.model.SearchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * PiSearchIndex — Fast substring-search index over the Pi digit string.
 *
 * APPROACH — Prefix Hash Index (simplified Rabin-Karp):
 * ─────────────────────────────────────────────────────
 * We pre-build a {@code Map<String, List<Integer>>} keyed on the first
 * {@value #INDEX_KEY_LEN} digits of every position in Pi.
 *
 * At query time:
 *  1. Hash the 6-char prefix of the search token → O(1) bucket lookup
 *  2. For each candidate position, measure how far the match extends → O(k)
 *     where k = bucket size (≈ 1 for 6-char keys in 1M digit Pi)
 *  3. Return the longest verified match
 *
 * COMPLEXITY:
 *  Build: O(N) time and space where N = Pi digit count
 *  Query: O(1) average, O(N) worst-case (degenerate hash collision)
 */
public class PiSearchIndex {

    private static final Logger log = LoggerFactory.getLogger(PiSearchIndex.class);

    /** Length of the key prefix stored in the index.
     *  6 digits → 10^6 possible keys, fitting well with 1M Pi digits (avg bucket size ≈ 1). */
    private static final int INDEX_KEY_LEN = 6;

    private final String piDigits;
    private final int    piLen;

    /**
     * Map from a 6-digit prefix to all Pi positions where that prefix appears.
     * Example: "141592" → [1, 100034, 522891, ...]
     */
    private final Map<String, List<Integer>> index;

    public PiSearchIndex(com.borko.rajkovic.pizip.service.PiDigitsService piDigitsService) {
        this.piDigits = piDigitsService.getDigits();
        this.piLen    = piDigits.length();
        log.info("Building search index over {} Pi digits...", piLen);
        this.index = buildIndex();
        log.info("Index ready — {} unique {}-digit prefixes indexed", index.size(), INDEX_KEY_LEN);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Index construction
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, List<Integer>> buildIndex() {
        // Pre-size the map to avoid rehashing — we expect ~1M entries
        Map<String, List<Integer>> idx = new HashMap<>((int)(piLen / 0.75) + 1);
        for (int i = 0; i <= piLen - INDEX_KEY_LEN; i++) {
            String key = piDigits.substring(i, i + INDEX_KEY_LEN);
            idx.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        return idx;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Find the longest match for {@code digitStr[start...]} in Pi.
     *
     * @param digitStr  The full digit string for the message being encoded.
     * @param start     Starting position within {@code digitStr}.
     * @param minLength Minimum match length to consider worthwhile.
     * @param mode      Controls how many candidates to check and when to stop early.
     * @return A {@link PiChunk} describing the best match, or {@code null} if none found.
     */
    public PiChunk findLongestMatch(String digitStr, int start, int minLength, SearchMode mode) {
        int remaining = digitStr.length() - start;
        if (remaining <= 0) return null;

        // Short tail (< INDEX_KEY_LEN chars): can't use the index, fall back to linear scan
        if (remaining < INDEX_KEY_LEN) {
            return bruteForceSearch(digitStr, start, remaining, minLength);
        }

        // Index lookup — O(1) bucket retrieval
        String prefix = digitStr.substring(start, start + INDEX_KEY_LEN);
        List<Integer> candidates = index.getOrDefault(prefix, Collections.emptyList());
        if (candidates.isEmpty()) return null;

        // Candidate limits and early-stop thresholds per mode
        int candidateLimit = switch (mode) {
            case FAST     -> Math.min(5,  candidates.size());
            case BALANCED -> Math.min(50, candidates.size());
            case BEST     ->             candidates.size();
        };
        int fastStopLen = switch (mode) {
            case FAST     -> 20;
            case BALANCED -> 50;
            case BEST     -> Integer.MAX_VALUE;
        };

        int bestLen = 0;
        int bestPos = -1;

        for (int ci = 0; ci < candidateLimit; ci++) {
            int piPos    = candidates.get(ci);
            int matchLen = measureMatch(digitStr, start, piPos, remaining);
            if (matchLen > bestLen) {
                bestLen = matchLen;
                bestPos = piPos;
                if (bestLen >= fastStopLen) break;
            }
        }

        if (bestLen < minLength || bestPos < 0) return null;
        return new PiChunk(bestPos, bestLen, 0);
    }

    /**
     * Character-by-character match length between {@code digitStr[inputPos...]}
     * and {@code piDigits[piPos...]}.
     */
    private int measureMatch(String digitStr, int inputPos, int piPos, int maxLen) {
        int len      = 0;
        int inputEnd = inputPos + maxLen;
        while (inputPos + len < inputEnd
               && piPos + len < piLen
               && digitStr.charAt(inputPos + len) == piDigits.charAt(piPos + len)) {
            len++;
        }
        return len;
    }

    /**
     * Linear scan fallback for short tails (< 6 digits remaining).
     * Only called at the very end of a message — acceptable overhead.
     */
    private PiChunk bruteForceSearch(String digitStr, int start, int remaining, int minLength) {
        String target = digitStr.substring(start, start + remaining);
        int idx = piDigits.indexOf(target);
        return (idx >= 0 && remaining >= minLength) ? new PiChunk(idx, remaining, 0) : null;
    }

    /** Expose Pi digit string for the decoder. */
    public String getPiDigits() { return piDigits; }
}
