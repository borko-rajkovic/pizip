package com.borko.rajkovic.pizip.controller;

import com.borko.rajkovic.pizip.config.PiZipProperties;
import com.borko.rajkovic.pizip.model.PiZipDTOs;
import com.borko.rajkovic.pizip.model.PiZipDTOs.DecodeResponse;
import com.borko.rajkovic.pizip.model.PiZipDTOs.EncodeResponse;
import com.borko.rajkovic.pizip.model.SearchMode;
import com.borko.rajkovic.pizip.service.PiZipService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${pizip.web.path:/api/pizip}")
@ConditionalOnProperty(
        prefix = "pizip.web",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PiZipController {

    private static final Logger log = LoggerFactory.getLogger(PiZipController.class);

    private final PiZipService service;
    private final PiZipProperties props;

    public PiZipController(PiZipService service, PiZipProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/encode")
    public ResponseEntity<EncodeResponse> encode(
            @Valid @RequestBody PiZipDTOs.EncodeRequest request) {

        SearchMode mode = (request.searchMode() != null)
                ? request.searchMode()
                : props.getDefaultSearchMode();

        log.info("POST /encode [length={}, mode={}]", request.text().length(), mode);

        EncodeResponse response = service.encode(request.text(), mode);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/decode")
    public ResponseEntity<DecodeResponse> decode(
            @Valid @RequestBody PiZipDTOs.DecodeRequest request) {

        log.info("POST /decode [length={}]", request.encoded().length());

        DecodeResponse response = service.decode(request.encoded());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<ServiceInfo> info() {
        return ResponseEntity.ok(new ServiceInfo(
                "PiZip",
                "1.0.0",
                props.getDefaultSearchMode().name()
        ));
    }

    public record ServiceInfo(
            String name,
            String version,
            String defaultMode
    ) {
    }
}
