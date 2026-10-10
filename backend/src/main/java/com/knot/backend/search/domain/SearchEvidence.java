package com.knot.backend.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "search_evidences")
public class SearchEvidence {

    @EmbeddedId
    private SearchEvidenceId id;

    @Column(nullable = false)
    private short rank;

    protected SearchEvidence() {}
}
