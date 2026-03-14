package com.borko.rajkovic.pizip.model;

/**
 * PiChunk — Represents one segment of an encoded message.
 *
 * An encoded message is a sequence of chunks, each of which is either:
 *
 *  1. A PI_REFERENCE: a pointer into the Pi digit string.
 *     Serialized as: {@code piIndex:length:offset}  e.g. {@code 48023:9:0}
 *
 *  2. A LITERAL: raw bytes that had no suitable match in Pi.
 *     Serialized as: {@code L:hexstring}  e.g. {@code L:48692074}
 *
 * The full encoded message is chunks joined by {@code ;}.
 *
 * WHY OFFSET?
 * ──────────
 * Offset allows a chunk to skip the first N digits of a Pi match before
 * the real data starts. This is an extension hook for future optimizations
 * (e.g. sharing Pi runs across chunks). Currently always 0.
 */
public class PiChunk {

    public enum Type { PI_REFERENCE, LITERAL }

    public final Type type;

    // For PI_REFERENCE chunks:
    public final int piIndex;      // start position in the Pi digit string
    public final int length;       // number of Pi digits matched
    public final int offset;       // digits to skip at start (extension hook, usually 0)

    // For LITERAL chunks:
    public final byte[] literalBytes; // raw bytes stored directly

    /** Construct a Pi-reference chunk. */
    public PiChunk(int piIndex, int length, int offset) {
        this.type = Type.PI_REFERENCE;
        this.piIndex = piIndex;
        this.length = length;
        this.offset = offset;
        this.literalBytes = null;
    }

    /** Construct a literal fallback chunk. */
    public PiChunk(byte[] literalBytes) {
        this.type = Type.LITERAL;
        this.piIndex = -1;
        this.length = literalBytes.length;
        this.offset = 0;
        this.literalBytes = literalBytes.clone(); // defensive copy
    }

    /**
     * Serialize to wire format.
     * Pi-ref:  {@code "48023:9:0"}
     * Literal: {@code "L:48692074686572652e"}
     */
    @Override
    public String toString() {
        if (type == Type.PI_REFERENCE) {
            return piIndex + ":" + length + ":" + offset;
        } else {
            StringBuilder sb = new StringBuilder("L:");
            for (byte b : literalBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    /**
     * Deserialize from wire format. Inverse of {@link #toString()}.
     *
     * @throws IllegalArgumentException if the format is unrecognized.
     */
    public static PiChunk fromString(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Cannot parse empty chunk string");
        }
        if (s.startsWith("L:")) {
            String hex = s.substring(2);
            if (hex.length() % 2 != 0) {
                throw new IllegalArgumentException("Literal hex string has odd length: " + hex);
            }
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return new PiChunk(bytes);
        } else {
            String[] parts = s.split(":");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid Pi-reference chunk format: " + s);
            }
            int piIndex = Integer.parseInt(parts[0]);
            int length  = Integer.parseInt(parts[1]);
            int offset  = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            if (piIndex < 0 || length <= 0 || offset < 0) {
                throw new IllegalArgumentException(
                    "Pi-reference chunk has invalid values: piIndex=" + piIndex +
                    " length=" + length + " offset=" + offset);
            }
            return new PiChunk(piIndex, length, offset);
        }
    }
}
