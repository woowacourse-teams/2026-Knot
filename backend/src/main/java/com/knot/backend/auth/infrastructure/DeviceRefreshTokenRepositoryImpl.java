package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.DeviceRefreshToken;
import com.knot.backend.auth.domain.DeviceRefreshTokenRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceRefreshTokenRepositoryImpl implements DeviceRefreshTokenRepository {
    private final DeviceRefreshTokenJpaRepository deviceRefreshTokenJpaRepository;

    @Override
    public DeviceRefreshToken save(DeviceRefreshToken token) {
        return deviceRefreshTokenJpaRepository.saveAndFlush(token);
    }

    @Override
    public Optional<DeviceRefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return deviceRefreshTokenJpaRepository.findByTokenHashForUpdate(tokenHash);
    }
}
