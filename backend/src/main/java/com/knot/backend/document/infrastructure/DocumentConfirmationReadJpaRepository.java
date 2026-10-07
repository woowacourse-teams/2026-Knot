package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.Document;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface DocumentConfirmationReadJpaRepository extends Repository<Document, Long> {

    @Query("""
            select new com.knot.backend.document.infrastructure.DocumentConfirmationSummaryRow(
                d.id,
                sum(case when c.confirmedAt is not null then 1 else 0 end),
                sum(case when c.id.documentId is not null and c.confirmedAt is null
                    and active.id is not null then 1 else 0 end),
                sum(case when c.id.documentId is not null and c.confirmedAt is null
                    and active.id is null then 1 else 0 end),
                case when mine.confirmedAt is not null then true else false end
            )
            from Document d
            left join DocumentConfirmation mine
                on mine.id.documentId = d.id and mine.id.memberId = :memberId
            left join DocumentConfirmation c on c.id.documentId = d.id
            left join WorkspaceMember active
                on active.workspaceId = d.workspaceId and active.memberId = c.id.memberId
                    and active.leftAt is null
            where d.workspaceId = :workspaceId and d.id = :documentId
            group by d.id, mine.confirmedAt
            """)
    Optional<DocumentConfirmationSummaryRow> findSummary(
            long workspaceId,
            long documentId,
            long memberId
    );
}
