package com.knot.backend.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "document_confirmations")
public class DocumentConfirmation {
    @EmbeddedId
    private DocumentConfirmationId id;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected DocumentConfirmation() {}

    public void confirm(Instant confirmedAt) {
        if (this.confirmedAt != null) {
            return;
        }
        if (confirmedAt == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
        this.confirmedAt = confirmedAt;
    }

    private DocumentConfirmation(
            long documentId,
            long memberId
    ) {
        this.id = DocumentConfirmationId.of(
                documentId,
                memberId
        );
    }

    public static DocumentConfirmation require(
            long documentId,
            long memberId
    ) {
        return new DocumentConfirmation(
                documentId,
                memberId
        );
    }
}
