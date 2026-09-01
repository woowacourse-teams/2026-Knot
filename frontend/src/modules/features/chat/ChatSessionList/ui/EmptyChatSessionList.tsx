import styled from "@emotion/styled";

/**
 * 워크스페이스에 아직 대화가 하나도 없을 때 목록 자리에 놓이는 안내.
 *
 * 대화가 없는 게 오류가 아니라는 것과, 어떻게 하면 목록이 채워지는지를 알려줍니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1254-5857
 */
export default function EmptyChatSessionList() {
  return (
    <Container>
      <Title>아직 나눈 대화가 없어요</Title>
      <Description>새 대화를 시작하면 여기에 기록이 남아요</Description>
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
  text-align: center;
`;

const Title = styled.strong`
  ${({ theme }) => theme.text.heading01};
  color: ${({ theme }) => theme.neutral[0]};
`;

const Description = styled.span`
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[500]};
`;
