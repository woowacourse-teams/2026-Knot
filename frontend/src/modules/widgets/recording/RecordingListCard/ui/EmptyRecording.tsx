import styled from "@emotion/styled";

/** 진행 중인 녹음이 없을 때 칸 대신 보여 주는 안내. */
export default function EmptyRecording() {
  return (
    <Container>
      <Message>진행 중인 녹음이 없어요</Message>
      <Description>
        독의 마이크로 녹음을 시작하면 여기에서 진행 상황을 볼 수 있어요
      </Description>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.125rem; /* 2px */
  width: 100%;
  min-height: 0;
  padding: 0.75rem; /* 12px */
  border-radius: 1rem; /* 16px */
  text-align: center;
`;

const Message = styled.p`
  color: ${({ theme }) => theme.neutral[700]};
  ${({ theme }) => theme.text.body01};
`;

const Description = styled.p`
  color: ${({ theme }) => theme.neutral[500]};
  ${({ theme }) => theme.text.caption02};
`;
