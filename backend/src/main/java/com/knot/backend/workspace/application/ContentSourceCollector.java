package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ContentCollectionResult;

public interface ContentSourceCollector {

    ContentCollectionResult collect(String accessCredential);
}
