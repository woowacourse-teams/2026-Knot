package com.knot.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthControllerTest {

    @Test
    @DisplayName("인증된 member 정보를 응답 DTO로 반환한다")
    void me_success() {
        // given
        AuthController controller = new AuthController();
        AuthenticatedMember member =
                AuthenticatedMember.of(1L, 42L, "octocat", "https://example.com/avatar");

        // when
        AuthenticatedMemberResponse result = controller.me(member);

        // then
        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.githubId()).isEqualTo(42L);
        assertThat(result.nickname()).isEqualTo("octocat");
    }
}
