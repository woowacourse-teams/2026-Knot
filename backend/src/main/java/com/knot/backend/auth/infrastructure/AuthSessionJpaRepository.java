package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.AuthSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);
}
