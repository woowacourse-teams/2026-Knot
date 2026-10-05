import styled from "@emotion/styled";

/**
 * 질문이 아직 없는 빈 대화에서 대화 칸 가운데에 띄우는 안내 문구.
 *
 * 첫 질문을 보내기 전까지만 보이며, 무엇을 할 수 있는 화면인지 알려 줘요.
 */
export default function EmptyConversation() {
  return (
    <Container>
      <Title>무엇을 찾고 있나요?</Title>
      <Description>모아 둔 문서에서 찾아 근거와 함께 답해요.</Description>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem; /* 8px */
  height: 100%;
  text-align: center;
`;

const Title = styled.h2`
  color: ${({ theme }) => theme.neutral[800]};
  ${({ theme }) => theme.text.title01};
`;

const Description = styled.p`
  color: ${({ theme }) => theme.neutral[600]};
  ${({ theme }) => theme.text.body01};
`;
