import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import type { AgentBridgeStatus } from "@/shared/types/desktop";
import { formatDateTime } from "@/shared/utils/formatDateTime";

import {
  AGENT_CONNECTION_MESSAGE,
  AGENT_STATUS_LABEL,
} from "../constants/agentConnection";

interface AgentStatusPanelProps {
  /** 아직 읽지 못했으면 null */
  status: AgentBridgeStatus | null;
  onRefresh: () => void;
}

const getStatusLabel = (status: AgentBridgeStatus | null) => {
  if (status === null) return AGENT_STATUS_LABEL.loading;
  if (status.running) return AGENT_STATUS_LABEL.running;

  return status.error === null
    ? AGENT_STATUS_LABEL.stopped
    : AGENT_STATUS_LABEL.failed;
};

/**
 * 로컬 MCP 서버의 상태 표.
 *
 * 마지막 도구 호출 시각은 CLI가 실제로 Knot에 붙었는지 확인하는 유일한 단서예요(로드맵 R26).
 * 기동 실패 사유(`error`)는 포트 충돌처럼 사용자가 고칠 수 있는 정보라 그대로 보여 줍니다.
 */
export default function AgentStatusPanel({
  status,
  onRefresh,
}: AgentStatusPanelProps) {
  const isFailed = status !== null && !status.running && status.error !== null;

  return (
    <Container aria-label="MCP 서버 상태">
      <Row>
        <Term>상태</Term>
        <Detail $isFailed={isFailed}>{getStatusLabel(status)}</Detail>
      </Row>
      {isFailed && <ErrorMessage role="alert">{status.error}</ErrorMessage>}

      <Row>
        <Term>주소</Term>
        <Detail>{status?.url ?? "-"}</Detail>
      </Row>

      <Row>
        <Term>마지막 도구 호출</Term>
        <Detail>
          {status?.lastToolCallAt
            ? formatDateTime(status.lastToolCallAt)
            : AGENT_CONNECTION_MESSAGE.noToolCall}
        </Detail>
      </Row>

      <Row>
        <Term>연결 토큰 발급</Term>
        <Detail>{status ? formatDateTime(status.tokenIssuedAt) : "-"}</Detail>
      </Row>

      <Actions>
        <Button size="sm" variant="outline" onClick={onRefresh}>
          상태 새로고침
        </Button>
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

const Detail = styled.dd<{ $isFailed?: boolean }>`
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
