import styled from "@emotion/styled";
import useNavigateToWorkspaceCode from "@hooks/domain/workspace/useNavigateToWorkspaceCode";
import Button from "@primitives/ui/Button";

import InvalidInvitationUrlIllustration from "@/assets/illustrations/invalidInvitationUrl.svg";

/**
 * 초대 링크 오류 안내.
 *
 * 만료되었거나 잘못된 초대 링크로 들어온 사용자에게 카드 없이 이유를 알리고,
 * `초대 코드 직접 입력하기`로 초대 코드 입력(`/workspace/code`)에 이어 줘요.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 위젯은 로고 아래 내용만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10148 올바르지 않은 초대 링크 접근}
 */
export default function WorkspaceJoinErrorNotice() {
  const { navigateToWorkspaceCode } = useNavigateToWorkspaceCode();

  return (
    <Container>
      <Content>
        <Illustration />

        <Header>
          <Title>초대장을 열 수 없어요</Title>
          <Description>
            <p>만료되었거나 잘못된 링크예요.</p>
            <p>초대한 분께 다시 요청해 보세요.</p>
          </Description>
        </Header>
      </Content>

      <Button size="lg" isFullWidth onClick={navigateToWorkspaceCode}>
        초대 코드 직접 입력하기
      </Button>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rem; /* 64px */
  width: 100%;
  max-width: 22.5rem; /* 360px */
`;

const Content = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2.5rem; /* 40px */
`;

const Illustration = styled(InvalidInvitationUrlIllustration)`
  flex-shrink: 0;
  width: 3rem; /* 48px */
  height: 3rem;
  color: ${({ theme }) => theme.neutral[800]};
`;

const Header = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem; /* 16px */
  text-align: center;
  overflow-wrap: break-word;
`;

const Title = styled.h1`
  color: ${({ theme }) => theme.neutral[800]};
  ${({ theme }) => theme.text.heading02};
`;

const Description = styled.div`
  color: ${({ theme }) => theme.neutral[700]};
  ${({ theme }) => theme.text.body02};
`;
