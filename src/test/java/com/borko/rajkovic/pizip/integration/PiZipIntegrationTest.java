package com.borko.rajkovic.pizip.integration;

import com.borko.rajkovic.pizip.model.PiZipDTOs;
import com.borko.rajkovic.pizip.model.SearchMode;
import com.borko.rajkovic.pizip.service.PiDigitsService;
import com.borko.rajkovic.pizip.service.PiZipService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests for PiZip.
 *
 * APPROACH — @SpringBootTest with a test configuration override:
 * ─────────────────────────────────────────────────────────────
 * We load the complete application context but replace PiDigitsService with a
 * fast stub (200 digits of Pi hardcoded). This means:
 *  - All Spring wiring is tested (auto-configuration, bean injection, etc.)
 *  - All layers are exercised (controller → service → search index → decode)
 *  - Tests still run in ~100ms instead of minutes
 *
 * The @TestConfiguration with @Primary ensures the stub bean wins over the
 * real PiDigitsService defined in PiZipAutoConfiguration.
 *
 * @TestPropertySource overrides application.yaml to disable file caching
 * and keep digit count small — defensive measure in case the real bean sneaks in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "pizip.digits=200",
    "pizip.cache-file=target/test-pi-cache.txt",
    "pizip.default-search-mode=BALANCED",
    "pizip.web.enabled=true",
    "pizip.web.path=/api/pizip"
})
@DisplayName("PiZip Integration Tests")
class PiZipIntegrationTest {

    /** First 200 digits of Pi — enough for meaningful encode/decode round-trips. */
    private static final String TEST_PI =
        "31415926535897932384626433832795028841971693993751" +
        "05820974944592307816406286208998628034825342117067" +
        "98214808651328230664709384460955058223172535940812" +
        "84811174502841027019385211055596446229489549303819";

    @Autowired MockMvc      mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired PiZipService pizipService; // used for direct service-layer assertions

    // ── Full HTTP round-trip ──────────────────────────────────────────────────

    @ParameterizedTest(name = "''{0}''")
    @DisplayName("POST /encode → POST /decode round-trip recovers original text")
    @ValueSource(strings = {
        "Hello, World!",
        "Pi is delicious!",
        "ABCDEFGHIJ",
        "1234567890",
        "Test message 123"
    })
    void encodeDecodeShouldRoundTripViaHttp(String originalText) throws Exception {
        // Step 1: encode via HTTP
        var encodeRequest = new PiZipDTOs.EncodeRequest(originalText, SearchMode.BALANCED);

        String encodeResponseJson = mockMvc.perform(post("/api/pizip/encode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(encodeRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        PiZipDTOs.EncodeResponse encodeResponse =
                objectMapper.readValue(encodeResponseJson, PiZipDTOs.EncodeResponse.class);

        assertThat(encodeResponse.encoded()).isNotBlank();
        assertThat(encodeResponse.originalText()).isEqualTo(originalText);

        // Step 2: decode via HTTP using the encoded value from step 1
        var decodeRequest = new PiZipDTOs.DecodeRequest(encodeResponse.encoded());

        String decodeResponseJson = mockMvc.perform(post("/api/pizip/decode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(decodeRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        PiZipDTOs.DecodeResponse decodeResponse =
                objectMapper.readValue(decodeResponseJson, PiZipDTOs.DecodeResponse.class);

        assertThat(decodeResponse.text()).isEqualTo(originalText);
    }

    // ── GET /info ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /info returns correct configuration values")
    void infoEndpointShouldReturnConfiguration() throws Exception {
        mockMvc.perform(get("/api/pizip/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("PiZip"))
            .andExpect(jsonPath("$.defaultMode").value("BALANCED"));
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /decode with malformed chunk returns 422 with error body")
    void decodeWithMalformedChunkShouldReturn422() throws Exception {
        var request = new PiZipDTOs.DecodeRequest("this_is_not_a_valid_chunk");

        mockMvc.perform(post("/api/pizip/decode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
            .andExpect(jsonPath("$.path").value("/api/pizip/decode"));
    }

    @Test
    @DisplayName("POST /decode with Pi-ref out of bounds returns 422")
    void decodeWithOutOfBoundsPiRefShouldReturn422() throws Exception {
        // piIndex=999999 far exceeds our 200-digit test Pi
        var request = new PiZipDTOs.DecodeRequest("999999:300:0");

        mockMvc.perform(post("/api/pizip/decode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("out of bounds")));
    }

    @Test
    @DisplayName("POST /encode with null text returns 400 with validation error")
    void encodeWithNullTextShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/pizip/encode")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    // ── Service-layer integration ─────────────────────────────────────────────

    @Test
    @DisplayName("BEST mode produces fewest or equal chunks compared to BALANCED")
    void bestModeShouldProduceOptimalChunkCount() {
        String input   = "Hello World";
        int best      = pizipService.encode(input, SearchMode.BEST).totalChunks();
        int balanced  = pizipService.encode(input, SearchMode.BALANCED).totalChunks();
        assertThat(best).isLessThanOrEqualTo(balanced);
    }

    @Test
    @DisplayName("compressionRatio is positive and non-zero")
    void compressionRatioShouldBePositive() {
        var result = pizipService.encode("Hello");
        assertThat(result.compressionRatio()).isPositive();
    }

    // ── Test configuration override ───────────────────────────────────────────

    /**
     * Provides a fast PiDigitsService stub for the integration test context.
     *
     * @Primary ensures this bean wins over the one created by PiZipAutoConfiguration.
     * @TestConfiguration marks this as test-only — it won't be picked up in production.
     */
    @TestConfiguration
    static class TestPiConfig {

        @Bean
        @Primary
        public PiDigitsService piDigitsService() {
            // Use the package-visible test constructor — no I/O, no computation
            return new PiDigitsService(TEST_PI) {};
        }
    }
}
