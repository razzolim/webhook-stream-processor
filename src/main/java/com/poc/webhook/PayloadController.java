package com.poc.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payload")
public class PayloadController {

    private static final Logger log = LoggerFactory.getLogger(PayloadController.class);

    private static final long MAX_ACCEPTED_BYTES = 10L * 1024 * 1024;

    private static final int MAX_LOG_CHARS = 10_000;
}
