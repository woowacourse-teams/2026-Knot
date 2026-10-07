package com.knot.backend.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@Embeddable
public class DocumentConfirmationId implements Serializable {
    @Column(name = "document_id", nullable = false)
    private long documentId;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    protected DocumentConfirmationId() {}

    private DocumentConfirmationId(
            long documentId,
            long memberId
    ) {
        if (documentId <= 0 || memberId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_DATA);
        }
        this.documentId = documentId;
        this.memberId = memberId;
    }

    public static DocumentConfirmationId of(
            long documentId,
            long memberId
    ) {
        return new DocumentConfirmationId(
                documentId,
                memberId
        );
    }
}
