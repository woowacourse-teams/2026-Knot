package com.knot.backend.document.domain;

import java.util.Optional;

public interface DocumentConfirmationRepository {

    Optional<DocumentConfirmation> findByDocumentIdAndMemberId(
            long documentId,
            long memberId
    );

    void flush();
}
