package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.RefreshTokenHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RefreshTokenHistoryJpaRepository extends JpaRepository<RefreshTokenHistory, Long> {
    Optional<RefreshTokenHistory> findByRefreshTokenHash(String refreshTokenHash);
}
