package com.knowledgeengine.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final int DIMENSIONS = 768;

    public String generateEmbedding(String text) {
        double[] vector = new double[DIMENSIONS];
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

        long seed = 0L;
        for (byte value : digest) {
            seed = (seed * 31L) + (value & 0xFFL);
        }

        double magnitude = 0.0;
        for (int i = 0; i < DIMENSIONS; i++) {
            long combined = seed + i * 131L + text.length() * 17L;
            double v = Math.sin(combined * 0.61803398875) * 0.5 + 0.5;
            double variation = ((digest[(i * 3) % digest.length] & 0xFF) / 255.0) * 0.25;
            vector[i] = v + variation;
            magnitude += vector[i] * vector[i];
        }

        magnitude = Math.sqrt(magnitude);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < DIMENSIONS; i++) {
            double normalized = magnitude == 0 ? 0.0 : vector[i] / magnitude;
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.US, "%.8f", normalized));
        }

        return "[" + builder + "]";
    }
}
