import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

interface RegistrationItemProps {
  title: string;
  description: string;
  /** 복사 뒤 main이 돌려준, 토큰을 가린 미리보기. 아직 복사하지 않았으면 undefined */
  preview?: string;
  isCopied: boolean;
  onCopy: () => void;
}

/**
 * CLI 하나의 등록 스니펫 행.
 *
 * 토큰 값은 이 화면으로 내려오지 않아요. `복사`를 누르면 셸의 main이 클립보드에 쓰고,
 * 여기서는 가려진 미리보기와 2초 동안의 `복사됨`만 보여 줍니다(로드맵 Q48).
 */
export default function RegistrationItem({
  title,
  description,
  preview,
  isCopied,
  onCopy,
}: RegistrationItemProps) {
  return (
    <Container role="group" aria-label={title}>
      <Header>
        <TextGroup>
          <Title>{title}</Title>
          <Description>{description}</Description>
        </TextGroup>

        <Button
          size="sm"
          variant={isCopied ? "accent" : "filled"}
          onClick={onCopy}
        >
          {isCopied ? "복사됨" : "복사"}
        </Button>
      </Header>

      {preview !== undefined && <Preview>{preview}</Preview>}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
  width: 100%;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem; /* 16px */
`;

const TextGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.25rem; /* 4px */
  min-width: 0;
`;

const Title = styled.h3`
  ${({ theme }) => theme.text.label01};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Description = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[600]};
`;

/** 명령·설정은 줄이 길어 글자를 줄이지 않고 가로 스크롤로 보여 줘요 */
const Preview = styled.pre`
  width: 100%;
  padding: 0.75rem 1rem; /* 12px 16px */
  border-radius: 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.neutral[100]};
  color: ${({ theme }) => theme.neutral[800]};
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.8125rem; /* 13px */
  line-height: 1.6;
  white-space: pre;
  overflow-x: auto;
`;
