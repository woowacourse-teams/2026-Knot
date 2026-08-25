package com.knot.backend.member.infrastructure;

import com.knot.backend.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByGithubId(long githubId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO member (github_id, nickname, profile_image_url)
                    VALUES (:githubId, :nickname, :profileImageUrl)
                    ON CONFLICT (github_id)
                    DO UPDATE SET nickname = EXCLUDED.nickname,
                                  profile_image_url = EXCLUDED.profile_image_url
                    """,
            nativeQuery = true)
    int saveLoginProfile(
            @Param("githubId") long githubId,
            @Param("nickname") String nickname,
            @Param("profileImageUrl") String profileImageUrl);
}
