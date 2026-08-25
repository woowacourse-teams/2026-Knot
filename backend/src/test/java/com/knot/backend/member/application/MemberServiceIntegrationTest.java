package com.knot.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.domain.Member;
import com.knot.backend.member.infrastructure.MemberRepository;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
class MemberServiceIntegrationTest {

    private final MemberService memberService;

    private final MemberRepository memberRepository;

    MemberServiceIntegrationTest(MemberService memberService, MemberRepository memberRepository) {
        this.memberService = memberService;
        this.memberRepository = memberRepository;
    }

    @BeforeEach
    void clearMembers() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("동시에 같은 GitHub 사용자가 로그인해도 member 하나만 유지한다")
    void login_concurrentFirstRequests_success() throws Exception {
        // given
        OAuthUser oauthUser = OAuthUser.of(42L, "octocat", "https://example.com/avatar");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Member> loginTask =
                () -> {
                    ready.countDown();
                    start.await();
                    return memberService.login(oauthUser);
                };

        try {
            Future<Member> first = executor.submit(loginTask);
            Future<Member> second = executor.submit(loginTask);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            start.countDown();
            Member firstMember = first.get(10, TimeUnit.SECONDS);
            Member secondMember = second.get(10, TimeUnit.SECONDS);

            // then
            assertThat(firstMember.getGithubId()).isEqualTo(42L);
            assertThat(secondMember.getGithubId()).isEqualTo(42L);
            assertThat(memberRepository.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
