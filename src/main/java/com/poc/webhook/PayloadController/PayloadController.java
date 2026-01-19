package com.poc.webhook.PayloadController;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@RestController
@RequestMapping("/payload")
public class PayloadController {

    private static final Logger log = LoggerFactory.getLogger(PayloadController.class);

    private static final long MAX_ACCEPTED_BYTES = 50L * 1024 * 1024;
    private static final int MAX_LOG_CHARS = 10_000;
    private static final int BUFFER_SIZE = 8 * 1024;

    @PostMapping(
            path = "/ingest-stream",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> ingestStream(HttpServletRequest request) throws IOException {

        try (InputStream in = request.getInputStream()) {

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long totalBytes = 0;

            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);

            StringBuilder preview = new StringBuilder(Math.min(MAX_LOG_CHARS, 2048));

            byte[] buf = new byte[BUFFER_SIZE];
            int read;

            while ((read = in.read(buf)) != -1) {
                totalBytes += read;

                if (totalBytes > MAX_ACCEPTED_BYTES) {
                    log.warn("Rejected /payload/ingest-stream: bytesRead={} exceeds max={} (remote={})",
                            totalBytes, MAX_ACCEPTED_BYTES, request.getRemoteAddr());
                    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                            .body("Payload too large");
                }

                sha256.update(buf, 0, read);

                if (preview.length() < MAX_LOG_CHARS) {
                    appendUtf8Preview(decoder, buf, read, preview, MAX_LOG_CHARS);
                }
            }

            if (preview.length() < MAX_LOG_CHARS) {
                flushDecoder(decoder, preview, MAX_LOG_CHARS);
            }

            String hashHex = toHex(sha256.digest());
            String previewText = preview.length() >= MAX_LOG_CHARS
                    ? preview.substring(0, MAX_LOG_CHARS) + "…(truncated)"
                    : preview.toString();

            log.info("Received /payload/ingest-stream: bytes={}, sha256={}, preview={}",
                    totalBytes, hashHex, previewText);

            return ResponseEntity.ok("OK");
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error");
        }
    }

    private static void appendUtf8Preview(
            CharsetDecoder decoder,
            byte[] bytes,
            int len,
            StringBuilder preview,
            int maxChars
    ) {
        ByteBuffer inBuf = ByteBuffer.wrap(bytes, 0, len);

        CharBuffer out = CharBuffer.allocate(2048);

        while (inBuf.hasRemaining() && preview.length() < maxChars) {
            out.clear();
            CoderResult result = decoder.decode(inBuf, out, false);
            out.flip();

            int remaining = maxChars - preview.length();
            int toAppend = Math.min(out.remaining(), remaining);

            if (toAppend > 0) {
                preview.append(out, 0, toAppend);
            }

            if (result.isError()) {
                break;
            }
        }
    }

    private static void flushDecoder(CharsetDecoder decoder, StringBuilder preview, int maxChars) {
        CharBuffer out = CharBuffer.allocate(2048);

        decoder.decode(ByteBuffer.allocate(0), out, true);
        decoder.flush(out);
        out.flip();

        int remaining = maxChars - preview.length();
        int toAppend = Math.min(out.remaining(), remaining);

        if (toAppend > 0) {
            preview.append(out, 0, toAppend);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
