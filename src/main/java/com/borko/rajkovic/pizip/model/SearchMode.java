package com.borko.rajkovic.pizip.model;

/**
 * SearchMode — controls the trade-off between encoding speed and compression ratio.
 *
 * <pre>
 * ┌──────────┬────────────┬─────────────┬─────────────────────────────────────┐
 * │ Mode     │ Speed      │ Compression │ Algorithm                           │
 * ├──────────┼────────────┼─────────────┼─────────────────────────────────────┤
 * │ FAST     │ XXX        │ XX          │ Greedy, 5 candidates, stop at 20    │
 * │ BALANCED │ XX         │ XXX         │ Greedy, 50 candidates, stop at 50   │
 * │ BEST     │ X          │ XXXX        │ Dynamic programming (optimal)       │
 * └──────────┴────────────┴─────────────┴─────────────────────────────────────┘
 * </pre>
 *
 * Configure the default via {@code pizip.default-search-mode} in application.yaml.
 */
public enum SearchMode {

    /**
     * Checks only 5 Pi candidates per token and stops once a match of 20+ digits is found.
     * Fastest encoding, slightly larger output. Good for interactive/real-time use.
     */
    FAST,

    /**
     * Checks up to 50 candidates per token. Good balance for most applications.
     * This is the default.
     */
    BALANCED,

    /**
     * Exhaustively finds the globally minimal number of chunks using dynamic programming.
     * Slowest encoding, best compression. Recommended for batch/offline processing.
     */
    BEST
}
