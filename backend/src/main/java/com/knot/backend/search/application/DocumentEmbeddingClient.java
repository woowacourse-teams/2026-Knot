package com.knot.backend.search.application;

import java.util.List;

public interface DocumentEmbeddingClient {

    List<double[]> embed(List<String> texts);
}
