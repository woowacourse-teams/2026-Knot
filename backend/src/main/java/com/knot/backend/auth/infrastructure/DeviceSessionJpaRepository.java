package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.DeviceSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceSessionJpaRepository extends JpaRepository<DeviceSession, Long> {

    Optional<DeviceSession> findByIdAndMemberId(
            Long id,
            Long memberId
    );

    boolean existsByIdAndMemberIdAndRevokedAtIsNull(
            Long id,
            Long memberId
    );

    @Query("""
            SELECT s
            FROM DeviceSession s
            WHERE s.memberId = :memberId
              AND s.revokedAt IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM DeviceRefreshToken t
                  WHERE t.sessionId = s.id
                    AND t.rotatedAt IS NULL
                    AND t.expiresAt > :now
              )
            ORDER BY s.lastUsedAt DESC, s.id DESC
            """)
    List<DeviceSession> findActiveByMemberId(
            @Param("memberId") Long memberId,
            @Param("now") Instant now
    );
}
