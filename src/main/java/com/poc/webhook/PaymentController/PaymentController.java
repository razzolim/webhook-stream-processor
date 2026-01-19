package com.poc.webhook.PaymentController;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private static final int MAX_LOG_CHARS = 10_000;
    private static final int BUFFER_SIZE = 8 * 1024;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> ingestStream(HttpServletRequest request) throws IOException {

        try (InputStream in = request.getInputStream()) {

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long totalBytes = 0;

            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);

            StringBuilder preview = new StringBuilder(Math.min(MAX_LOG_CHARS, 2048));

            byte[] carry = new byte[4];
            int carryLen = 0;

            byte[] buf = new byte[BUFFER_SIZE];
            int read;

            while ((read = in.read(buf)) != -1) {
                totalBytes += read;

                sha256.update(buf, 0, read);

                if (preview.length() < MAX_LOG_CHARS) {
                    carryLen = appendUtf8Preview(decoder, carry, carryLen, buf, read, preview, MAX_LOG_CHARS);
                }
            }

            if (preview.length() < MAX_LOG_CHARS) {
                flushDecoder(decoder, carry, carryLen, preview, MAX_LOG_CHARS);
            }

            String hashHex = toHex(sha256.digest());
            String previewText = preview.length() >= MAX_LOG_CHARS ? preview.substring(0, MAX_LOG_CHARS) + "…(truncated)" : preview.toString();

            log.info("Received /payload: bytes={}, sha256={}, preview={}", totalBytes, hashHex, previewText);

            return ResponseEntity.accepted().build();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error");
        }
    }

    private static int appendUtf8Preview(CharsetDecoder decoder, byte[] carry, int carryLen, byte[] bytes, int len, StringBuilder preview, int maxChars) {
        final ByteBuffer inBuf;

        if (carryLen == 0) {
            inBuf = ByteBuffer.wrap(bytes, 0, len);
        } else {
            byte[] combined = new byte[carryLen + len];
            System.arraycopy(carry, 0, combined, 0, carryLen);
            System.arraycopy(bytes, 0, combined, carryLen, len);
            inBuf = ByteBuffer.wrap(combined);
        }

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

            if (result.isOverflow()) {
                continue;
            }
            if (result.isUnderflow() || result.isError()) {
                break;
            }
        }
        int leftover = Math.min(inBuf.remaining(), carry.length);
        if (leftover > 0) {
            inBuf.get(carry, 0, leftover);
        }
        return leftover;
    }

    private static void flushDecoder(CharsetDecoder decoder, byte[] carry, int carryLen, StringBuilder preview, int maxChars) {
        CharBuffer out = CharBuffer.allocate(2048);
        ByteBuffer tail = (carryLen > 0) ? ByteBuffer.wrap(carry, 0, carryLen) : ByteBuffer.allocate(0);

        while (preview.length() < maxChars) {
            out.clear();
            CoderResult result = decoder.decode(tail, out, true);
            out.flip();

            int remaining = maxChars - preview.length();
            int toAppend = Math.min(out.remaining(), remaining);
            if (toAppend > 0) {
                preview.append(out, 0, toAppend);
            }

            if (result.isOverflow()) continue;
            break;
        }

        while (preview.length() < maxChars) {
            out.clear();
            CoderResult result = decoder.flush(out);
            out.flip();

            int remaining = maxChars - preview.length();
            int toAppend = Math.min(out.remaining(), remaining);
            if (toAppend > 0) {
                preview.append(out, 0, toAppend);
            }

            if (result.isOverflow()) continue;
            break;
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
