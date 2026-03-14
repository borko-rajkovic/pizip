package com.borko.rajkovic.pizip.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
public class PiDigitsService {

    private static final Logger log = LoggerFactory.getLogger(PiDigitsService.class);

    private final String digits;

    /**
     * Package-visible constructor for testing — accepts a pre-computed digit string
     * directly, bypassing all file I/O and Pi computation.
     *
     * Test stubs can subclass PiDigitsService and call super(digits) to inject
     * a known digit string without touching the file system.
     */
    public PiDigitsService(String precomputedDigits) {
        this.digits = precomputedDigits;
        log.debug("PiDigitsService initialised with {} pre-computed digits (test/stub mode)",
                precomputedDigits.length());
    }

    public PiDigitsService() throws IOException {
        Instant start = Instant.now();
        this.digits = loadDigits();
        log.info("PiDigitsService ready: {} digits loaded in {}",
                digits.length(), Duration.between(start, Instant.now()));
    }

    private String loadDigits() throws IOException {
        return new ClassPathResource("pi_digits.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    /** Returns the full Pi digit string (no decimal point, starts with '3'). */
    public String getDigits() { return digits; }

    /** Total number of available Pi digits. */
    public int length() { return digits.length(); }
}
