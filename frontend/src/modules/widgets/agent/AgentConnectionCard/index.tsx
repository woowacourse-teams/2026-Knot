import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import Divider from "@primitives/ui/Divider";

import {
  AGENT_REGISTRATION_TARGETS,
  OWNER_NOTICE,
} from "./constants/agentConnection";
import { useAgentConnection } from "./model/useAgentConnection";
import AgentStatusPanel from "./ui/AgentStatusPanel";
import DesktopOnlyNotice from "./ui/DesktopOnlyNotice";
import PortForm from "./ui/PortForm";
import RegistrationItem from "./ui/RegistrationItem";
import TokenRotateSection from "./ui/TokenRotateSection";

/**
 * CLI 에이전트 연결 카드(데스크톱 전용, 기획서 6.4·로드맵 S9).
 *
 * 데스크톱 앱이 띄운 로컬 MCP 서버의 상태를 보여 주고, 사용자의 CLI(Claude Code·Codex CLI·
 * Gemini CLI)에 Knot을 등록하는 명령·설정과 Knot 스킬 설치 명령을 복사하게 해요.
 * 연결 토큰은 화면으로 내려오지 않고 셸의 main이 클립보드에 직접 씁니다.
 * 포트 변경과 토큰 재발급도 여기서 해요. 문서 본문이 사용자의 CLI와 그 모델 제공자로
 * 전송된다는 고지(로드맵 R27)를 함께 둡니다.
 *
 * `window.knotDesktop.agent`가 없는 브라우저에서는 데스크톱 앱에서 이어가라는 안내만 그려요.
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 */
export default function AgentConnectionCard() {
  const {
    isAvailable,
    status,
    handleRefresh,
    copiedTarget,
    previews,
    copyError,
    handleCopy,
    portValue,
    portError,
    isPortApplying,
    handlePortChange,
    handlePortSubmit,
    isRotateConfirming,
    isRotating,
    rotateError,
    handleRotateRequest,
    handleRotateCancel,
    handleRotateConfirm,
    handleGoToWorkspace,
  } = useAgentConnection();

  if (!isAvailable) {
    return <DesktopOnlyNotice onGoToWorkspace={handleGoToWorkspace} />;
  }

  return (
    <Container>
      <Header>
        <Title>CLI 에이전트 연결</Title>
        <Description>
          터미널에서 쓰는 코딩 에이전트가 Knot의 팀 문서를 검색해 답하게 해요.
          <br />
          아래 명령을 복사해 각 CLI에 한 번만 등록하면 돼요.
        </Description>
      </Header>

      <AgentStatusPanel status={status} onRefresh={handleRefresh} />

      <Section aria-labelledby="agent-connection-register">
        <SectionTitle id="agent-connection-register">
          CLI에 등록하기
        </SectionTitle>
        <ItemList>
          {AGENT_REGISTRATION_TARGETS.map(({ target, title, description }) => (
            <RegistrationItem
              key={target}
              title={title}
              description={description}
              preview={previews[target]}
              isCopied={copiedTarget === target}
              onCopy={() => void handleCopy(target)}
            />
          ))}
        </ItemList>
        {copyError && <ErrorMessage role="alert">{copyError}</ErrorMessage>}
      </Section>

      <Section aria-labelledby="agent-connection-settings">
        <SectionTitle id="agent-connection-settings">서버 설정</SectionTitle>
        <ItemList>
          <PortForm
            value={portValue}
            errorMessage={portError}
            isApplying={isPortApplying}
            onChange={handlePortChange}
            onSubmit={() => void handlePortSubmit()}
          />
          <TokenRotateSection
            isConfirming={isRotateConfirming}
            isRotating={isRotating}
            errorMessage={rotateError}
            onRequest={handleRotateRequest}
            onConfirm={() => void handleRotateConfirm()}
            onCancel={handleRotateCancel}
          />
        </ItemList>
      </Section>

      <Notice>{OWNER_NOTICE}</Notice>

      <Divider />

      <Button
        size="lg"
        variant="outline"
        isFullWidth
        onClick={handleGoToWorkspace}
      >
        워크스페이스로 이동
      </Button>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 1.75rem; /* 28px */
  width: 100%;
  max-width: 40rem; /* 640px — 명령이 한 줄에 보이도록 초대 카드보다 넓어요 */
  padding: 3rem; /* 48px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

const Header = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem; /* 12px */
  text-align: center;
  overflow-wrap: break-word;
`;

const Title = styled.h1`
  color: ${({ theme }) => theme.neutral[900]};
  ${({ theme }) => theme.text.heading02};
`;

const Description = styled.p`
  color: ${({ theme }) => theme.neutral[600]};
  ${({ theme }) => theme.text.body01};
`;

const Section = styled.section`
  display: flex;
  flex-direction: column;
  gap: 1rem; /* 16px */
  width: 100%;
`;

const SectionTitle = styled.h2`
  ${({ theme }) => theme.text.heading01};
  color: ${({ theme }) => theme.neutral[800]};
`;

const ItemList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.25rem; /* 20px */
`;

const ErrorMessage = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.sub.warning[800]};
`;

const Notice = styled.p`
  padding: 1rem 1.25rem; /* 16px 20px */
  border-radius: 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.neutral[100]};
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[700]};
`;
