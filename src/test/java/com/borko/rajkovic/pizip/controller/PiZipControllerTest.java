package com.borko.rajkovic.pizip.controller;

import com.borko.rajkovic.pizip.config.PiZipProperties;
import com.borko.rajkovic.pizip.exception.DecodingException;
import com.borko.rajkovic.pizip.model.PiZipDTOs;
import com.borko.rajkovic.pizip.model.SearchMode;
import com.borko.rajkovic.pizip.service.PiZipService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for {@link PiZipController}.
 *
 * APPROACH — @WebMvcTest (slice test):
 * ────────────────────────────────────
 * @WebMvcTest loads only the web layer (controllers, filters, exception handlers)
 * without starting a full application context. This means:
 *  - No Spring Boot auto-configuration runs
 *  - No PiDigitsService / PiZipService beans are created
 *  - Tests run in ~milliseconds
 *
 * We use @MockBean to inject a Mockito mock of PiZipService, giving us full
 * control over what it returns — letting us test every controller scenario without
 * any Pi computation at all.
 *
 * WHY @Import(PiZipExceptionHandler.class)?
 * @WebMvcTest doesn't automatically pick up @RestControllerAdvice classes outside
 * the slice. We import it explicitly so our exception-handling tests work correctly.
 */
@WebMvcTest(PiZipController.class)
@Import({PiZipExceptionHandler.class})
@DisplayName("PiZipController")
class PiZipControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean PiZipService pizipService;
    @MockitoBean PiZipProperties pizipProperties;

    // ── POST /encode ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /encode")
    class EncodeEndpoint {

        @Test
        @DisplayName("200 OK with valid request body")
        void shouldReturn200ForValidRequest() throws Exception {
            var request  = new PiZipDTOs.EncodeRequest("Hello", SearchMode.BALANCED);
            var response = new PiZipDTOs.EncodeResponse(
                    "48023:9:0", "Hello", 5, 8, 1.6, 1, 0, 1, "BALANCED");

            when(pizipProperties.getDefaultSearchMode()).thenReturn(SearchMode.BALANCED);
            when(pizipService.encode(eq("Hello"), eq(SearchMode.BALANCED))).thenReturn(response);

            mockMvc.perform(post("/api/pizip/encode")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encoded").value("48023:9:0"))
                .andExpect(jsonPath("$.originalText").value("Hello"))
                .andExpect(jsonPath("$.searchMode").value("BALANCED"))
                .andExpect(jsonPath("$.piRefChunks").value(1))
                .andExpect(jsonPath("$.literalChunks").value(0));
        }

        @Test
        @DisplayName("uses default mode when searchMode is null in request")
        void shouldUseDefaultModeWhenNotSpecified() throws Exception {
            var request  = new PiZipDTOs.EncodeRequest("Hi", null);
            var response = new PiZipDTOs.EncodeResponse(
                    "L:4869", "Hi", 2, 6, 3.0, 0, 1, 1, "FAST");

            when(pizipProperties.getDefaultSearchMode()).thenReturn(SearchMode.FAST);
            when(pizipService.encode(eq("Hi"), eq(SearchMode.FAST))).thenReturn(response);

            mockMvc.perform(post("/api/pizip/encode")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchMode").value("FAST"));

            // Verify service was called with the default mode (FAST, not null)
            verify(pizipService).encode(eq("Hi"), eq(SearchMode.FAST));
        }

        @Test
        @DisplayName("400 Bad Request when text is null")
        void shouldReturn400WhenTextIsNull() throws Exception {
            mockMvc.perform(post("/api/pizip/encode")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        @DisplayName("400 Bad Request when text is empty")
        void shouldReturn400WhenTextIsEmpty() throws Exception {
            var request = new PiZipDTOs.EncodeRequest("", null);
            mockMvc.perform(post("/api/pizip/encode")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request when text exceeds max length")
        void shouldReturn400WhenTextTooLong() throws Exception {
            var request = new PiZipDTOs.EncodeRequest("x".repeat(10_001), null);
            mockMvc.perform(post("/api/pizip/encode")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request when Content-Type is not JSON")
        void shouldReturn400WhenContentTypeIsNotJson() throws Exception {
            mockMvc.perform(post("/api/pizip/encode")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("Hello"))
                .andExpect(status().is4xxClientError());
        }
    }

    // ── POST /decode ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /decode")
    class DecodeEndpoint {

        @Test
        @DisplayName("200 OK with valid encoded string")
        void shouldReturn200ForValidRequest() throws Exception {
            var request  = new PiZipDTOs.DecodeRequest("48023:9:0");
            var response = new PiZipDTOs.DecodeResponse("Hello");

            when(pizipService.decode("48023:9:0")).thenReturn(response);

            mockMvc.perform(post("/api/pizip/decode")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Hello"));
        }

        @Test
        @DisplayName("422 Unprocessable Entity when encoded string is malformed")
        void shouldReturn422WhenDecodingFails() throws Exception {
            var request = new PiZipDTOs.DecodeRequest("totally:invalid:garbage:format");

            when(pizipService.decode(any()))
                    .thenThrow(new DecodingException("Malformed chunk"));

            mockMvc.perform(post("/api/pizip/decode")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Malformed chunk"));
        }

        @Test
        @DisplayName("400 Bad Request when encoded is null")
        void shouldReturn400WhenEncodedIsNull() throws Exception {
            mockMvc.perform(post("/api/pizip/decode")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"encoded\": null}"))
                .andExpect(status().isBadRequest());
        }
    }

    // ── GET /info ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /info")
    class InfoEndpoint {

        @Test
        @DisplayName("200 OK with service info")
        void shouldReturnServiceInfo() throws Exception {
            when(pizipProperties.getDefaultSearchMode()).thenReturn(SearchMode.BALANCED);

            mockMvc.perform(get("/api/pizip/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PiZip"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.defaultMode").value("BALANCED"));
        }
    }
}
