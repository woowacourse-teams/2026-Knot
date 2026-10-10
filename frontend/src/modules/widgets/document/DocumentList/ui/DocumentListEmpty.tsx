import styled from "@emotion/styled";

/**
 * 문서가 하나도 없을 때 목록 자리에 보여 주는 안내. 피그마 「문서 목록/빈 상태」의 Empty예요.
 */
export default function DocumentListEmpty() {
  return (
    <Container>
      <Title>아직 문서가 없어요</Title>
      <Description>
        독의 마이크로 회의를 녹음하면 주제별로 정리된 문서가 폴더에 모여요
      </Description>
    </Container>
  );
}

/** 피그마 Empty: 높이 320px의 가운데에 글 두 줄, 줄 사이 4px */
const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.25rem; /* 4px */
  height: 20rem; /* 320px */
  text-align: center;
`;

const Title = styled.p`
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[700]};
`;

const Description = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[500]};
`;
