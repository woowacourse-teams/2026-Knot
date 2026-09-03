package com.knot.backend.search.infrastructure;

import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.application.SearchProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class FakeDocumentEmbeddingClient implements DocumentEmbeddingClient {
    private final SearchProperties properties;

    FakeDocumentEmbeddingClient(SearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        properties.validate();
        List<double[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(embedOne(text));
        }
        return List.copyOf(embeddings);
    }

    private double[] embedOne(String text) {
        double[] embedding = new double[1024];
        String normalized = text == null ? "" : text.toLowerCase();
        String[] tokens = normalized.split("[^\\p{L}\\p{N}_-]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            addHash(
                    embedding,
                    token,
                    1.0
            );
            addCharacterNgrams(
                    embedding,
                    token
            );
        }
        if (isZero(embedding)) {
            embedding[0] = 1.0;
        }
        normalize(embedding);
        return embedding;
    }

    private void addCharacterNgrams(
            double[] embedding,
            String token
    ) {
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        for (int start = 0; start + 2 < bytes.length; start++) {
            String ngram = new String(
                    bytes,
                    start,
                    3,
                    StandardCharsets.UTF_8
            );
            addHash(
                    embedding,
                    ngram,
                    0.25
            );
        }
    }

    private void addHash(
            double[] embedding,
            String value,
            double weight
    ) {
        int index = Math.floorMod(
                value.hashCode(),
                embedding.length
        );
        embedding[index] += weight;
    }

    private boolean isZero(double[] embedding) {
        for (double value : embedding) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private void normalize(double[] embedding) {
        double length = 0;
        for (double value : embedding) {
            length += value * value;
        }
        double norm = Math.sqrt(length);
        for (int index = 0; index < embedding.length; index++) {
            embedding[index] /= norm;
        }
    }
}
