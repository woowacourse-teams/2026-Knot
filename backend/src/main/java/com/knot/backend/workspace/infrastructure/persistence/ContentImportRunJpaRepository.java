package com.knot.backend.workspace.infrastructure.persistence;

import com.knot.backend.workspace.domain.ContentImportRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ContentImportRunJpaRepository extends JpaRepository<ContentImportRun, Long> {

    @Query("""
            SELECT importRun
            FROM ContentImportRun importRun
            WHERE importRun.id = :importRunId
                AND EXISTS (
                    SELECT workspaceMember.id
                    FROM WorkspaceMember workspaceMember
                    WHERE workspaceMember.workspaceId = importRun.workspaceId
                        AND workspaceMember.memberId = :memberId
                )
            """)
    Optional<ContentImportRun> findVisibleByIdAndMemberId(
            Long importRunId,
            long memberId
    );
}
