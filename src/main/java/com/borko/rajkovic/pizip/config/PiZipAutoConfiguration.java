package com.borko.rajkovic.pizip.config;

import com.borko.rajkovic.pizip.service.PiDigitsService;
import com.borko.rajkovic.pizip.service.PiZipService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;

@AutoConfiguration
@EnableConfigurationProperties(PiZipProperties.class)
@ComponentScan(basePackages = "com.borko.rajkovic.pizip.controller")
public class PiZipAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PiDigitsService piDigitsService() throws IOException {
        return new PiDigitsService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PiZipService piZipService(PiDigitsService piDigitsService,
                                           PiZipProperties props) {
        return new PiZipService(piDigitsService, props.getDefaultSearchMode());
    }
}
