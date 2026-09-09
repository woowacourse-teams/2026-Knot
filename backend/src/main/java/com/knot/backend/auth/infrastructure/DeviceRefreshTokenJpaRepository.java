package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.DeviceRefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRefreshTokenJpaRepository extends JpaRepository<DeviceRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DeviceRefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<DeviceRefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
