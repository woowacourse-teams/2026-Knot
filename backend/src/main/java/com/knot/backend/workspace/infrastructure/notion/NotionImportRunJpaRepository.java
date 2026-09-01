package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionImportRun;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface NotionImportRunJpaRepository extends JpaRepository<NotionImportRun, Long> {

    @Query(value = """
            SELECT *
            FROM notion_import_runs
            WHERE status = 'PENDING'
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotionImportRun> findFirstPendingForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT importRun
            FROM NotionImportRun importRun
            WHERE importRun.id = :importRunId
            """)
    Optional<NotionImportRun> findByIdForUpdate(Long importRunId);

    @Query(value = """
            SELECT *
            FROM notion_import_runs
            WHERE (status = 'PENDING' AND created_at <= :pendingCutoff)
                OR (status = 'RUNNING' AND started_at <= :runningCutoff)
            ORDER BY created_at ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotionImportRun> findStaleForUpdate(
            Instant pendingCutoff,
            Instant runningCutoff,
            int batchSize
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
