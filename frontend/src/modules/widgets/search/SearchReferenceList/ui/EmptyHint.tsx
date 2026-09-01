import styled from "@emotion/styled";

/**
 * 아직 펼쳐 둔 근거 문서가 없을 때 레일에 보여주는 안내.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1158-5469
 */
export default function EmptyHint() {
  return (
    <Container>
      <Description>질문을 남기면 팀 문서에서 근거를 찾아드려요</Description>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
`;

const Description = styled.p`
  text-align: center;
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[500]};
`;
