import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import type { LlmSubscriptionStatus } from "@/shared/types/desktop";
import { formatDateTime } from "@/shared/utils/formatDateTime";

import {
  ANSWERED_BY_LABEL,
  CLAUDE_SUBSCRIPTION_MESSAGE,
  SUBSCRIPTION_STATUS_LABEL,
} from "../constants/claudeSubscription";

interface SubscriptionStatusPanelProps {
  /** 아직 읽지 못했으면 null */
  status: LlmSubscriptionStatus | null;
  isSigningIn: boolean;
  isSigningOut: boolean;
  errorMessage?: string;
  onSignIn: () => void;
  onSignOut: () => void;
}

const getStatusLabel = (status: LlmSubscriptionStatus | null) => {
  if (status === null) return SUBSCRIPTION_STATUS_LABEL.loading;

  return status.signedIn
    ? SUBSCRIPTION_STATUS_LABEL.signedIn
    : SUBSCRIPTION_STATUS_LABEL.signedOut;
};

const getAnsweredByLabel = (status: LlmSubscriptionStatus | null) => {
  if (status === null || status.lastAnsweredBy === null) {
    return ANSWERED_BY_LABEL.none;
  }

  return ANSWERED_BY_LABEL[status.lastAnsweredBy];
};

/**
 * 내 Claude 구독의 로그인 상태 표.
 *
 * 토큰 값은 셸에만 있어 여기에는 만료 시각만 와요. 마지막 오류 코드는 크레딧 소진·거절처럼
 * 사용자가 원인을 짐작할 수 있는 정보라 그대로 보여 줍니다(로드맵 R29).
 */
export default function SubscriptionStatusPanel({
  status,
  isSigningIn,
  isSigningOut,
  errorMessage,
  onSignIn,
  onSignOut,
}: SubscriptionStatusPanelProps) {
  const isSignedIn = status?.signedIn === true;

  return (
    <Container aria-label="Claude 구독 상태">
      <Row>
        <Term>상태</Term>
        <Detail $isSignedIn={isSignedIn}>{getStatusLabel(status)}</Detail>
      </Row>

      <Row>
        <Term>액세스 토큰 만료</Term>
        <Detail>
          {status?.expiresAt
            ? formatDateTime(status.expiresAt)
            : CLAUDE_SUBSCRIPTION_MESSAGE.noExpiry}
        </Detail>
      </Row>

      <Row>
        <Term>마지막 답변 경로</Term>
        <Detail>{getAnsweredByLabel(status)}</Detail>
      </Row>

      <Row>
        <Term>마지막 오류</Term>
        <Detail $isFailed={status?.lastError !== null}>
          {status?.lastError ?? CLAUDE_SUBSCRIPTION_MESSAGE.noError}
        </Detail>
      </Row>

      {errorMessage && <ErrorMessage role="alert">{errorMessage}</ErrorMessage>}

      <Actions>
        {isSignedIn ? (
          <Button
            size="sm"
            variant="outline"
            isLoading={isSigningOut}
            onClick={onSignOut}
          >
            구독 로그아웃
          </Button>
        ) : (
          <Button size="sm" isLoading={isSigningIn} onClick={onSignIn}>
            Claude로 로그인
          </Button>
        )}
      </Actions>
    </Container>
  );
}

const Container = styled.dl`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
  width: 100%;
  padding: 1.25rem 1.5rem; /* 20px 24px */
  border-radius: 1rem; /* 16px */
  background-color: ${({ theme }) => theme.neutral[50]};
`;

const Row = styled.div`
  display: flex;
  gap: 1rem; /* 16px */
  justify-content: space-between;
  align-items: baseline;
`;

const Term = styled.dt`
  flex-shrink: 0;
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[500]};
`;

const Detail = styled.dd<{ $isSignedIn?: boolean; $isFailed?: boolean }>`
  min-width: 0;
  ${({ theme }) => theme.text.label01};
  color: ${({ theme, $isFailed }) =>
    $isFailed ? theme.sub.warning[800] : theme.neutral[900]};
  text-align: right;
  overflow-wrap: anywhere;
`;

const ErrorMessage = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.sub.warning[800]};
  overflow-wrap: anywhere;
`;

const Actions = styled.div`
  display: flex;
  justify-content: flex-end;
`;
