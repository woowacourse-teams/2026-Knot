package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.DeviceAuthorizationCode;
import com.knot.backend.auth.domain.DeviceAuthorizationCodeRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceAuthorizationCodeRepositoryImpl implements DeviceAuthorizationCodeRepository {
    private final DeviceAuthorizationCodeJpaRepository deviceAuthorizationCodeJpaRepository;

    @Override
    public DeviceAuthorizationCode save(DeviceAuthorizationCode code) {
        return deviceAuthorizationCodeJpaRepository.saveAndFlush(code);
    }

    @Override
    public Optional<DeviceAuthorizationCode> findByCodeHashForUpdate(String codeHash) {
        return deviceAuthorizationCodeJpaRepository.findByCodeHashForUpdate(codeHash);
    }

    @Override
    public int deleteExpiredBefore(Instant threshold) {
        return deviceAuthorizationCodeJpaRepository.deleteByExpiresAtBefore(threshold);
    }
}
