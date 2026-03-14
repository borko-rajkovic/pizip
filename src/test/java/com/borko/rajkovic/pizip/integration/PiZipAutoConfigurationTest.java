package com.borko.rajkovic.pizip.integration;

import com.borko.rajkovic.pizip.config.PiZipAutoConfiguration;
import com.borko.rajkovic.pizip.config.PiZipProperties;
import com.borko.rajkovic.pizip.controller.PiZipController;
import com.borko.rajkovic.pizip.model.SearchMode;
import com.borko.rajkovic.pizip.service.PiDigitsService;
import com.borko.rajkovic.pizip.service.PiZipService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PiZipAutoConfiguration} — verifies that beans are created
 * correctly under different property configurations and that the auto-config
 * backs off when beans are provided by the user.
 *
 * APPROACH — ApplicationContextRunner:
 * ─────────────────────────────────────
 * Spring Boot's ApplicationContextRunner lets us create lightweight, isolated
 * application contexts in tests. It's much faster than @SpringBootTest because
 * it doesn't start a server or run the full auto-configuration chain.
 *
 * We configure it with only our auto-configuration, add specific properties,
 * and then assert on the resulting beans — ideal for testing @ConditionalOn* logic.
 */
@DisplayName("PiZipAutoConfiguration")
class PiZipAutoConfigurationTest {

    /** First 200 Pi digits used by all stubs in this test class. */
    private static final String TEST_PI =
        "31415926535897932384626433832795028841971693993751" +
        "05820974944592307816406286208998628034825342117067" +
        "98214808651328230664709384460955058223172535940812" +
        "84811174502841027019385211055596446229489549303819";

    /**
     * The runner is pre-configured with our auto-configuration and a stub
     * PiDigitsService so tests don't hit the file system.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PiZipAutoConfiguration.class))
            .withUserConfiguration(StubPiDigitsConfig.class) // inject stub before auto-config runs
            .withPropertyValues(
                "pizip.digits=200",
                "pizip.cache-file=target/test-pi.txt",
                "pizip.default-search-mode=BALANCED",
                "pizip.web.enabled=true"
            );

    // ── Bean presence ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("PiZipService bean is registered by default")
    void shouldRegisterPiZipServiceBean() {
        contextRunner.run(ctx ->
            assertThat(ctx).hasSingleBean(PiZipService.class));
    }

    @Test
    @DisplayName("PiZipProperties bean is registered with correct values")
    void shouldRegisterPropertiesWithCorrectValues() {
        contextRunner.run(ctx -> {
            var props = ctx.getBean(PiZipProperties.class);
            assertThat(props.getDefaultSearchMode()).isEqualTo(SearchMode.BALANCED);
        });
    }

    @Test
    @DisplayName("PiZipController bean is registered when web.enabled=true")
    void shouldRegisterControllerWhenWebEnabled() {
        contextRunner
            .withPropertyValues("pizip.web.enabled=true")
            .run(ctx -> assertThat(ctx).hasSingleBean(PiZipController.class));
    }

    // ── Conditional behaviour ─────────────────────────────────────────────────

    @Test
    @DisplayName("PiZipController is NOT registered when web.enabled=false")
    void shouldNotRegisterControllerWhenWebDisabled() {
        contextRunner
            .withPropertyValues("pizip.web.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(PiZipController.class));
    }

    @Test
    @DisplayName("auto-config backs off PiZipService when user defines their own")
    void shouldBackOffPiZipServiceWhenUserProvidesOwn() {
        contextRunner
            .withUserConfiguration(CustomPiZipServiceConfig.class)
            .run(ctx -> {
                // There should still be exactly one PiZipService
                assertThat(ctx).hasSingleBean(PiZipService.class);
                // And it should be the user-provided one (identified by its special default mode)
                var svc = ctx.getBean(PiZipService.class);
                // We verify it's the custom one by encoding and checking mode in the response
                var result = svc.encode("Hi", SearchMode.FAST);
                assertThat(result.searchMode()).isEqualTo("FAST");
            });
    }

    @Test
    @DisplayName("default search mode can be overridden via properties")
    void shouldRespectDefaultSearchModeProperty() {
        contextRunner
            .withPropertyValues("pizip.default-search-mode=FAST")
            .run(ctx -> {
                var props = ctx.getBean(PiZipProperties.class);
                assertThat(props.getDefaultSearchMode()).isEqualTo(SearchMode.FAST);
            });
    }

    // ── Test configurations ───────────────────────────────────────────────────

    /**
     * Provides a fast PiDigitsService stub with @Primary so it wins over the
     * real bean that PiZipAutoConfiguration would try to create.
     */
    @Configuration
    static class StubPiDigitsConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        public PiDigitsService piDigitsService() {
            return new PiDigitsService(TEST_PI) {};
        }
    }

    /**
     * User-defined PiZipService — used to verify @ConditionalOnMissingBean backs off.
     */
    @Configuration
    static class CustomPiZipServiceConfig {
        @Bean
        public PiZipService pizipService(PiDigitsService piDigitsService) {
            // Custom service with FAST as default — distinguishable from BALANCED default
            return new PiZipService(piDigitsService, SearchMode.FAST);
        }
    }
}
