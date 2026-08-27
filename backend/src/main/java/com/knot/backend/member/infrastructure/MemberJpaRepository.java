package com.knot.backend.member.infrastructure;

import com.knot.backend.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {}
