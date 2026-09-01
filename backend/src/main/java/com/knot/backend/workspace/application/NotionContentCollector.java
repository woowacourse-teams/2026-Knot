package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionCollectionResult;

public interface NotionContentCollector {

    NotionCollectionResult collect(String accessCredential);
}
