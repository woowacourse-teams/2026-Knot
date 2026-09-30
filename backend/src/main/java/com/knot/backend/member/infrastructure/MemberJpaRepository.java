package com.knot.backend.member.infrastructure;

import com.knot.backend.member.domain.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Member> findWithLockById(Long memberId);
}
