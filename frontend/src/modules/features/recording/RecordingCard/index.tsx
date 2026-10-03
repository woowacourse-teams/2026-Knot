import styled from "@emotion/styled";
import Divider from "@primitives/ui/Divider";

/**
 * 녹음 화면 카드.
 */
export default function RecordingCard() {
  const me = "홍길동";

  return (
    <Container>
      <Header>
        <Title>{me ? `${me} 님의 녹음` : "녹음"}</Title>
        <Help>문서 제목은 녹음이 끝나면 주제별로 자동으로 붙어요.</Help>
      </Header>

      <Divider />

      <Guide>
        <p>
          다른 화면으로 이동해도 녹음은 계속돼요. 독을 누르면 이 화면으로
          돌아와요.
        </p>
        <p>녹음을 끝내면 대화를 주제별 문서로 정리해 문서 탭에 저장해요.</p>
      </Guide>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.25rem; /* 20px */
  width: 100%;
  padding: 1.75rem 2rem; /* 28px 32px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
`;

const Header = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
`;

const Title = styled.h1`
  color: ${({ theme }) => theme.neutral[800]};
  overflow-wrap: break-word;
  ${({ theme }) => theme.text.title01};
`;

const Help = styled.p`
  color: ${({ theme }) => theme.neutral[500]};
  ${({ theme }) => theme.text.caption02};
`;

const Guide = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
  color: ${({ theme }) => theme.neutral[600]};
  ${({ theme }) => theme.text.caption02};
`;
