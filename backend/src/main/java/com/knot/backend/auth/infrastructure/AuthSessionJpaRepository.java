package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.AuthSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select authSession from AuthSession authSession where authSession.refreshTokenHash = :refreshTokenHash")
    Optional<AuthSession> findByRefreshTokenHashForUpdate(@Param("refreshTokenHash") String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select authSession from AuthSession authSession where authSession.id = :authSessionId")
    Optional<AuthSession> findByIdForUpdate(@Param("authSessionId") long authSessionId);
}
