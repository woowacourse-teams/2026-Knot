package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.WorkspaceMember;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface WorkspaceMemberJpaRepository extends JpaRepository<WorkspaceMember, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WorkspaceMember> findAllByMemberIdOrderByIdAsc(Long memberId);

    Optional<WorkspaceMember> findByMemberIdAndLastViewedTrue(Long memberId);

    boolean existsByWorkspaceIdAndMemberId(
            Long workspaceId,
            Long memberId
    );
}
