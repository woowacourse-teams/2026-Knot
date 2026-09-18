package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceInvitationCredentialTest {

    @DisplayName("6자리 코드는 앞뒤 공백을 제거하고 대문자로 정규화한다")
    @Test
    void classify_success_normalizedCode() {
        // given
        String tokenOrCode = " x35d3s ";

        // when
        WorkspaceInvitationCredential credential = WorkspaceInvitationCredential.from(tokenOrCode);

        // then
        assertThat(credential.kind()).isEqualTo(WorkspaceInvitationSecretKind.INVITE_CODE);
        assertThat(credential.secret()).isEqualTo("X35D3S");
        assertThat(credential.rateLimited()).isTrue();
    }

    @DisplayName("허용 알파벳 32자에 속한 6자리 코드는 초대 코드로 분류한다")
    @Test
    void classify_success_codeAlphabet() {
        // given
        String tokenOrCode = "AHJNP9";

        // when
        WorkspaceInvitationCredential credential = WorkspaceInvitationCredential.from(tokenOrCode);

        // then
        assertThat(credential.kind()).isEqualTo(WorkspaceInvitationSecretKind.INVITE_CODE);
        assertThat(credential.secret()).isEqualTo("AHJNP9");
        assertThatCode(credential::validate).doesNotThrowAnyException();
    }

    @DisplayName("6자리가 아닌 값은 원문 그대로 링크 토큰으로 분류한다")
    @Test
    void classify_success_linkToken() {
        // given
        String tokenOrCode = "AbC_1234567890";

        // when
        WorkspaceInvitationCredential credential = WorkspaceInvitationCredential.from(tokenOrCode);

        // then
        assertThat(credential.kind()).isEqualTo(WorkspaceInvitationSecretKind.LINK_TOKEN);
        assertThat(credential.secret()).isEqualTo("AbC_1234567890");
        assertThat(credential.rateLimited()).isFalse();
    }

    @DisplayName("내부 공백이 포함된 6자리 값은 초대 없음으로 거부한다")
    @Test
    void classify_failure_internalBlankCode() {
        // given
        String tokenOrCode = "AB CD1";

        // when
        ThrowingCallable action = () -> WorkspaceInvitationCredential.from(tokenOrCode)
                .validate();

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
    }

    @DisplayName("금지 문자가 포함된 6자리 값은 초대 없음으로 거부한다")
    @Test
    void classify_failure_forbiddenCodeCharacter() {
        // given
        String tokenOrCode = "ABC1O0";

        // when
        ThrowingCallable action = () -> WorkspaceInvitationCredential.from(tokenOrCode)
                .validate();

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
    }

    @DisplayName("대문자 변환 후 6자리를 초과하는 코드 후보는 초대 없음으로 거부한다")
    @Test
    void classify_failure_uppercaseExpansion() {
        // given
        String tokenOrCode = "abcdeß";

        // when
        ThrowingCallable action = () -> WorkspaceInvitationCredential.from(tokenOrCode)
                .validate();

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
    }
}
