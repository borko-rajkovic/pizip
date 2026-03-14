package com.borko.rajkovic.pizip.config;

import com.borko.rajkovic.pizip.model.SearchMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@ConfigurationProperties(prefix = "pizip")
public class PiZipProperties {

    private SearchMode defaultSearchMode = SearchMode.BALANCED;

    private Web web = new Web();

    @Data
    public static class Web {

        private boolean enabled = true;

        private String path = "/api/pizip";
    }
}
