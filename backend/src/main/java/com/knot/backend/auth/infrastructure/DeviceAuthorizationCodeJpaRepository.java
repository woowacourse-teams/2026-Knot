package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.DeviceAuthorizationCode;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceAuthorizationCodeJpaRepository extends JpaRepository<DeviceAuthorizationCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DeviceAuthorizationCode c WHERE c.codeHash = :codeHash")
    Optional<DeviceAuthorizationCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DeviceAuthorizationCode c WHERE c.expiresAt < :threshold")
    int deleteByExpiresAtBefore(@Param("threshold") Instant threshold);
}
