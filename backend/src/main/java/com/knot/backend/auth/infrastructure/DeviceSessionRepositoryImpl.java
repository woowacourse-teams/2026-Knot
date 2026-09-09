package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.DeviceSession;
import com.knot.backend.auth.domain.DeviceSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceSessionRepositoryImpl implements DeviceSessionRepository {
    private final DeviceSessionJpaRepository deviceSessionJpaRepository;

    @Override
    public DeviceSession save(DeviceSession session) {
        return deviceSessionJpaRepository.saveAndFlush(session);
    }

    @Override
    public Optional<DeviceSession> findById(long sessionId) {
        return deviceSessionJpaRepository.findById(sessionId);
    }

    @Override
    public Optional<DeviceSession> findByIdAndMemberId(
            long sessionId,
            long memberId
    ) {
        return deviceSessionJpaRepository.findByIdAndMemberId(
                sessionId,
                memberId
        );
    }

    @Override
    public List<DeviceSession> findActiveByMemberId(
            long memberId,
            Instant now
    ) {
        return deviceSessionJpaRepository.findActiveByMemberId(
                memberId,
                now
        );
    }

    @Override
    public boolean existsActive(
            long sessionId,
            long memberId
    ) {
        return deviceSessionJpaRepository.existsByIdAndMemberIdAndRevokedAtIsNull(
                sessionId,
                memberId
        );
    }
}
