import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import Divider from "@primitives/ui/Divider";

import NotionIcon from "@/assets/icons/notion.svg";

import { useNotionConnect } from "./model/useNotionConnect";

/**
 * 노션 연동 카드.
 *
 * 워크스페이스 생성 플로우의 마지막 단계로, 노션에 쌓아둔 기록을 knot로 옮길지 물어요.
 * `노션 연결하기`는 연결 시작 API로 받은 Notion 인증 페이지로 이동하고, 돌아오면
 * `?result=connected`는 홈으로 보내고 `?result=failed`는 실패 화면(워크스페이스로 이동)을
 * 보여줍니다. `워크스페이스로 이동`은 연결 없이 홈으로 가요.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1946 새 워크스페이스 생성/노션에서 가져오기}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1949 Card/Onboarding & Workspace}
 */
export default function NotionConnectCard() {
  const { isFailed, isConnecting, errorMessage, handleConnect, handleGoHome } =
    useNotionConnect();

  if (isFailed) {
    return (
      <Container>
        <Icon />

        <Header>
          <Title>노션 연결에 실패했어요</Title>
          <Description>
            연결이 취소됐거나 문제가 생겼어요.
            <br />
            워크스페이스로 이동해 이용을 계속할 수 있어요.
          </Description>
        </Header>

        <Actions>
          <Button size="lg" isFullWidth onClick={handleGoHome}>
            워크스페이스로 이동
          </Button>
        </Actions>
      </Container>
    );
  }

  return (
    <Container>
      <Icon />

      <Header>
        <Title>노션 기록 이어가기</Title>
        <Description>
          노션에 쌓아둔 기록들을
          <br />
          knot로 옮겨올 수 있어요.
        </Description>
      </Header>

      <Actions>
        <Button
          size="lg"
          isFullWidth
          isLoading={isConnecting}
          onClick={handleConnect}
        >
          노션 연결하기
        </Button>
        {errorMessage && (
          <ErrorMessage role="alert">{errorMessage}</ErrorMessage>
        )}

        <Divider label="또는" />

        <Button size="lg" variant="outline" isFullWidth onClick={handleGoHome}>
          워크스페이스로 이동
        </Button>
      </Actions>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.75rem; /* 28px */
  width: 100%;
  max-width: 28.75rem; /* 460px */
  padding: 3rem; /* 48px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=542-261 Icon/Notion size=48} */
const Icon = styled(NotionIcon)`
  flex-shrink: 0;
  width: 3rem; /* 48px */
  height: 3rem;
  color: ${({ theme }) => theme.neutral[800]};
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

const Actions = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem; /* 12px */
  width: 100%;
`;

/** 입력창이 없어 TextField의 에러 문구와 같은 색·크기로 연결 버튼 아래에 띄워요. */
const ErrorMessage = styled.p`
  width: 100%;
  color: ${({ theme }) => theme.sub.warning[800]};
  text-align: center;
  ${({ theme }) => theme.text.caption02};
`;
