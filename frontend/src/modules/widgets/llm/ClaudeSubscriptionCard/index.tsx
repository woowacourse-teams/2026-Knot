import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import Divider from "@primitives/ui/Divider";

import {
  BILLING_NOTICE,
  FALLBACK_NOTICE,
  POLICY_NOTICE,
} from "./constants/claudeSubscription";
import { useClaudeSubscription } from "./model/useClaudeSubscription";
import DesktopOnlyNotice from "./ui/DesktopOnlyNotice";
import ModelSettingsForm from "./ui/ModelSettingsForm";
import SubscriptionStatusPanel from "./ui/SubscriptionStatusPanel";

/**
 * Claude 구독 카드(데스크톱 전용, 기획서 6.5·로드맵 L3).
 *
 * 앱 안 질문에 답하는 데 쓰는 내 Claude 구독의 로그인 상태를 보여 주고, 로그인·로그아웃과
 * 모델·effort 설정을 맡아요. 토큰은 셸의 main에만 있고 화면으로는 상태만 내려와요.
 * extra usage 크레딧 과금과 정책 리스크(로드맵 R28·R29), 서버 모델 폴백(로드맵 Q66)을
 * 숨기지 않고 함께 둡니다.
 *
 * `window.knotDesktop.llm`이 없는 브라우저에서는 데스크톱 앱에서 이어가라는 안내만 그려요.
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 */
export default function ClaudeSubscriptionCard() {
  const {
    isAvailable,
    status,
    isSigningIn,
    isSigningOut,
    authError,
    handleSignIn,
    handleSignOut,
    settings,
    modelValue,
    effortValue,
    isSettingsDirty,
    isSaving,
    isSaved,
    settingsError,
    handleModelChange,
    handleEffortChange,
    handleSettingsSubmit,
    handleGoToWorkspace,
  } = useClaudeSubscription();

  if (!isAvailable) {
    return <DesktopOnlyNotice onGoToWorkspace={handleGoToWorkspace} />;
  }

  return (
    <Container>
      <Header>
        <Title>Claude 구독</Title>
        <Description>
          앱에서 질문하면 내 Claude 구독으로 답해요.
          <br />
          로그인하지 않으면 서버 모델이 답해요.
        </Description>
      </Header>

      <SubscriptionStatusPanel
        status={status}
        isSigningIn={isSigningIn}
        isSigningOut={isSigningOut}
        errorMessage={authError}
        onSignIn={() => void handleSignIn()}
        onSignOut={() => void handleSignOut()}
      />

      <Section aria-labelledby="claude-subscription-settings">
        <SectionTitle id="claude-subscription-settings">모델 설정</SectionTitle>
        <ModelSettingsForm
          settings={settings}
          modelValue={modelValue}
          effortValue={effortValue}
          isDirty={isSettingsDirty}
          isSaving={isSaving}
          isSaved={isSaved}
          errorMessage={settingsError}
          onModelChange={handleModelChange}
          onEffortChange={handleEffortChange}
          onSubmit={() => void handleSettingsSubmit()}
        />
      </Section>

      <NoticeList aria-label="과금·정책 안내">
        <Notice>{BILLING_NOTICE}</Notice>
        <Notice>{POLICY_NOTICE}</Notice>
        <Notice>{FALLBACK_NOTICE}</Notice>
      </NoticeList>

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
  max-width: 40rem; /* 640px — CLI 에이전트 연결 카드와 같은 폭 */
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

const NoticeList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
`;

const Notice = styled.p`
  padding: 1rem 1.25rem; /* 16px 20px */
  border-radius: 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.neutral[100]};
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[700]};
`;
