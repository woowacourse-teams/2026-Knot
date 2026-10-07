package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.Document;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select new com.knot.backend.document.infrastructure.DocumentConfirmationTargetRow(
                c.id.memberId, m.nickname, m.profileImageUrl, c.confirmedAt,
                case when active.id is not null then true else false end
            )
            from DocumentConfirmation c
            join Document d on d.id = c.id.documentId
            join Member m on m.id = c.id.memberId
            left join WorkspaceMember active
                on active.workspaceId = d.workspaceId and active.memberId = c.id.memberId
                    and active.leftAt is null
            where d.workspaceId = :workspaceId and d.id = :documentId
                and (:afterCursor = false
                    or case when c.confirmedAt is not null then :confirmedOrder
                        when active.id is not null then :pendingOrder else :excludedOrder end > :cursorOrder
                    or (case when c.confirmedAt is not null then :confirmedOrder
                        when active.id is not null then :pendingOrder else :excludedOrder end = :cursorOrder
                        and c.id.memberId > :cursorMemberId))
            order by case when c.confirmedAt is not null then :confirmedOrder
                when active.id is not null then :pendingOrder else :excludedOrder end, c.id.memberId
            """)
    List<DocumentConfirmationTargetRow> findPage(
            long workspaceId,
            long documentId,
            int confirmedOrder,
            int pendingOrder,
            int excludedOrder,
            boolean afterCursor,
            int cursorOrder,
            long cursorMemberId,
            Pageable pageable
    );
}
