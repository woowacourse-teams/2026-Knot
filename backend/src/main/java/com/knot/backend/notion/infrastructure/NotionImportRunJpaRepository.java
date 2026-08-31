package com.knot.backend.notion.infrastructure;

import com.knot.backend.notion.domain.NotionImportRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface NotionImportRunJpaRepository extends JpaRepository<NotionImportRun, Long> {

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
