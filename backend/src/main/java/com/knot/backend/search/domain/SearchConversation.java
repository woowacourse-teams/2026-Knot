package com.knot.backend.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "search_conversations")
public class SearchConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private long workspaceId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private long memberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "visible_in_list", nullable = false)
    private boolean visibleInList;

    protected SearchConversation() {}

    private SearchConversation(
            long workspaceId,
            long memberId,
            Instant acceptedAt
    ) {
        validateIdentifier(workspaceId);
        validateIdentifier(memberId);
        validateAcceptedAt(acceptedAt);
        this.workspaceId = workspaceId;
        this.memberId = memberId;
        this.createdAt = acceptedAt;
        this.updatedAt = acceptedAt;
        this.visibleInList = true;
    }

    public static SearchConversation create(
            long workspaceId,
            long memberId,
            Instant acceptedAt
    ) {
        return new SearchConversation(
                workspaceId,
                memberId,
                acceptedAt
        );
    }

    public void recordFirstAnswerStatus(SearchMessageStatus status) {
        validateAnswerStatus(status);
        if (status == SearchMessageStatus.FAILED) {
            visibleInList = false;
            return;
        }
        if (status == SearchMessageStatus.COMPLETED) {
            visibleInList = true;
        }
    }

    private static void validateIdentifier(long id) {
        if (id <= 0) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateAcceptedAt(Instant acceptedAt) {
        if (acceptedAt == null) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private void validateAnswerStatus(SearchMessageStatus status) {
        if (status == null || status == SearchMessageStatus.RECEIVED) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }
}
