import styled from "@emotion/styled";

/**
 * 아직 대화가 없을 때 보여주는 안내.
 *
 * 무엇을 물어볼 수 있는지 알려주어 첫 질문을 유도합니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10065
 */
export default function EmptyHint() {
  return (
    <Container>
      <Title>어떤 문서를 찾으시나요?</Title>
      <Description>질문을 남기면 팀 문서에서 근거를 찾아드려요</Description>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  width: 100%;
  height: 100%;
`;

const Title = styled.strong`
  ${({ theme }) => theme.text.heading01};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Description = styled.span`
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[500]};
`;
