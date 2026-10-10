package com.knot.backend.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@Embeddable
public class SearchEvidenceId implements Serializable {

    @Column(name = "message_id", nullable = false)
    private long messageId;

    @Column(name = "document_id", nullable = false)
    private long documentId;

    protected SearchEvidenceId() {}
}
