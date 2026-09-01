package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface NotionImportRunJpaRepository extends JpaRepository<NotionImportRun, Long> {

    Optional<NotionImportRun> findFirstByContentSourceConnectionIdAndStatusIn(
            Long contentSourceConnectionId,
            Collection<NotionImportStatus> statuses
    );

    @Query("""
            SELECT importRun
            FROM NotionImportRun importRun
            WHERE importRun.id = :importRunId
                AND EXISTS (
                    SELECT workspaceMember.id
                    FROM WorkspaceMember workspaceMember
                    WHERE workspaceMember.workspaceId = importRun.workspaceId
                        AND workspaceMember.memberId = :memberId
                )
            """)
    Optional<NotionImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    );
}
