package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionPage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotionPageJpaRepository extends JpaRepository<NotionPage, Long> {

    List<NotionPage> findAllByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);
}
